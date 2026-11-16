# Práctica Calificada: Persistencia y Comunicación Multi-componente (S11 | AP8)

Arquitectura multi-componente desplegada en **Kubernetes (Minikube)** que integra un backend reactivo **Spring Boot WebFlux**, una base de datos **MongoDB con almacenamiento persistente (PVC/PV)**, configuración externalizada (**ConfigMap**), credenciales protegidas (**Secret**) y comunicación interna y externa entre componentes.

---

## 1. Introducción

Un contenedor es efímero: si el Pod se reinicia o se reprograma, lo que guardó en su sistema de archivos se pierde. Un backend que escribe datos reales necesita (a) almacenamiento que sobreviva al Pod, (b) configuración que cambie sin reconstruir la imagen, (c) credenciales fuera del código y (d) una forma estable de que los componentes se encuentren entre sí.

Este proyecto resuelve esos cuatro puntos:

| Necesidad | Mecanismo de Kubernetes | Archivo |
|---|---|---|
| Aislar el proyecto | Namespace | `00-namespace.yaml` |
| Datos que sobreviven al Pod | PersistentVolumeClaim (PVC) → PersistentVolume (PV) | `01-mongo-pvc.yaml` |
| Configuración pública | ConfigMap | `02-app-configmap.yaml` |
| Credenciales | Secret (Opaque, Base64) | `03-db-secret.yaml` |
| Base de datos accesible solo por dentro | Deployment + Service `ClusterIP` | `04-mongo-stack.yaml` |
| Backend con probes y acceso externo | Deployment + Service `NodePort` | `05-backend-stack.yaml` |

### Estructura del repositorio

```text
/
├── k8s/
│   ├── 00-namespace.yaml
│   ├── 01-mongo-pvc.yaml
│   ├── 02-app-configmap.yaml
│   ├── 03-db-secret.yaml
│   ├── 04-mongo-stack.yaml
│   └── 05-backend-stack.yaml
├── src/                                  # Spring Boot 3 + WebFlux + Reactive MongoDB
│   ├── Dockerfile                        # Multi-stage (Maven/JDK -> JRE)
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/practica/s11/
│       │   ├── PracticaS11Application.java
│       │   ├── controller/   (ProductoController, InfoController)
│       │   ├── service/      (ProductoService)
│       │   ├── repository/   (ProductoRepository)
│       │   ├── model/        (Producto, ProductoRequest)
│       │   └── exception/    (GlobalExceptionHandler)
│       └── resources/application.yml
├── README.md
└── .gitignore
```

---

## 2. Arquitectura

### 2.1 Diagrama textual

```text
                         HOST (tu PC)
                              │
             curl / navegador │  minikube service backend-service -n practica-s11 --url
                              ▼
┌───────────────────────── CLÚSTER MINIKUBE ───────────────────────────────────┐
│  ┌──────────────────────── Namespace: practica-s11 ────────────────────────┐ │
│  │                                                                         │ │
│  │   Service: backend-service  (NodePort 30011 → 8080)    ◄── tráfico EXTERNO
│  │                │                                                        │ │
│  │                ▼                                                        │ │
│  │   Deployment: backend-webflux  (Pod Spring WebFlux, puerto 8080)        │ │
│  │     ├─ envFrom → ConfigMap app-config  (DB_HOST, DB_PORT, DB_NAME,      │ │
│  │     │                                   APP_PORT)                       │ │
│  │     ├─ envFrom → Secret db-secret      (MONGO_INITDB_ROOT_USERNAME/     │ │
│  │     │                                   MONGO_INITDB_ROOT_PASSWORD)     │ │
│  │     └─ Probes: liveness + readiness → GET /actuator/health              │ │
│  │                │                                                        │ │
│  │                │  DNS interno (CoreDNS): mongo-service:27017            │ │
│  │                ▼                                                        │ │
│  │   Service: mongo-service  (ClusterIP 27017)   ◄── SOLO tráfico INTERNO  │ │
│  │                │                                                        │ │
│  │                ▼                                                        │ │
│  │   Deployment: mongo  (mongo:7.0)                                        │ │
│  │     ├─ env ← Secret db-secret (usuario/contraseña root)                 │ │
│  │     └─ volumeMount /data/db ──► PVC mongo-pvc (1Gi, RWO) ──► PV (hostPath)│
│  └─────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Flujo de una petición

1. El cliente llama a `http://<ip-minikube>:<nodePort>/api/productos`.
2. `backend-service` (NodePort) reenvía al Pod `backend-webflux` que esté **Ready**.
3. El backend resuelve `mongo-service` mediante CoreDNS (valor `DB_HOST` del ConfigMap) y abre la conexión al puerto 27017 con las credenciales del Secret.
4. MongoDB lee o escribe en `/data/db`, que está respaldado por el PV asociado al PVC.
5. La respuesta regresa como JSON por el mismo camino, sin bloquear hilos (Mono/Flux).

---

## 3. Justificación técnica

### 3.1 Persistencia: PVC y PV

- **PersistentVolume (PV):** pieza de almacenamiento real del clúster (en Minikube, un directorio `hostPath` en el nodo).
- **PersistentVolumeClaim (PVC):** la *solicitud* de almacenamiento que hace la aplicación (tamaño y modo de acceso). Kubernetes la enlaza con un PV adecuado; cuando ocurre, el PVC queda en estado **`Bound`**.
- **Provisión dinámica:** Minikube incluye el StorageClass `standard`; al crear `mongo-pvc` crea automáticamente el PV, sin definirlo a mano.
- **Desacople de ciclos de vida:** el volumen existe independientemente del Pod. Si el Pod se elimina o se reprograma, el nuevo Pod vuelve a montar el mismo PVC y MongoDB encuentra sus datos en `/data/db`.
- **`ReadWriteOnce`:** el volumen se monta en lectura/escritura desde un solo nodo, adecuado para una instancia MongoDB standalone. Por eso el Deployment usa `replicas: 1` y `strategy: Recreate` (evita que el Pod nuevo y el viejo compitan por el volumen durante una actualización).
- **Límite conocido:** el almacenamiento `hostPath` de Minikube es válido para el laboratorio. En producción se usaría almacenamiento de red/cloud y, para alta disponibilidad, un StatefulSet o un ReplicaSet de MongoDB.

### 3.2 ConfigMap vs Secret

| Aspecto | ConfigMap (`app-config`) | Secret (`db-secret`) |
|---|---|---|
| Contenido | `DB_HOST`, `DB_PORT`, `DB_NAME`, `APP_PORT` | `MONGO_INITDB_ROOT_USERNAME`, `MONGO_INITDB_ROOT_PASSWORD` |
| Sensibilidad | Pública | Confidencial |
| Formato en el manifiesto | Texto plano | Base64 |
| Quién lo consume | Backend (`envFrom`) | Backend (`envFrom`) y MongoDB (`secretKeyRef`) |
| Buena práctica | Versionable en Git | No versionar secretos reales; tratar con RBAC |

Beneficio común: la **misma imagen Docker** funciona en cualquier entorno; solo cambia la configuración inyectada. El backend lee las variables desde `application.yml` con la sintaxis `${VARIABLE}`; las credenciales no tienen valor por defecto, así que si faltan la aplicación falla al iniciar (*fail-fast*).

### 3.3 Aclaración sobre Base64

> **Base64 NO es cifrado.** Es una codificación reversible cuyo único propósito es representar datos binarios o con caracteres especiales como texto seguro para YAML/JSON.

```bash
echo -n 'admin' | base64                 # YWRtaW4=
echo 'YWRtaW4=' | base64 -d              # admin  (cualquiera puede revertirlo)
```

- Se usa `echo -n` para no codificar el salto de línea final (un error frecuente que provoca fallos de autenticación).
- Un Secret **sí** aporta ventajas respecto a un ConfigMap: Kubernetes lo trata como objeto sensible (permisos RBAC específicos, no se imprime en `describe`, puede cifrarse en reposo en etcd y montarse en memoria `tmpfs`).
- Pero **un Secret en un repositorio público queda expuesto**. Las credenciales de este repositorio (`admin` / `S11_Mongo_Segura_2026`) son **solo de demostración académica**. En producción: no versionarlos, habilitar cifrado en reposo y usar Sealed Secrets, External Secrets Operator o HashiCorp Vault.

### 3.4 Comunicación interna y externa

- **ClusterIP (`mongo-service`)**: IP virtual accesible solo desde dentro del clúster. MongoDB no queda expuesto al exterior, reduciendo la superficie de ataque.
- **NodePort (`backend-service`, 30011)**: abre un puerto en el nodo para pruebas externas (rango permitido 30000–32767).
- **CoreDNS**: dentro del mismo namespace basta el nombre corto `mongo-service`; el nombre completo es `mongo-service.practica-s11.svc.cluster.local`.

### 3.5 Sondas de salud (probes)

| Sonda | Pregunta que responde | Si falla |
|---|---|---|
| `readinessProbe` | ¿Puede recibir tráfico? | El Pod se retira de los endpoints del Service (no se reinicia) |
| `livenessProbe` | ¿Sigue vivo? | Kubernetes reinicia el contenedor |

Ambas apuntan a `/actuator/health` (exigido por la guía). Ese endpoint agrega el estado de MongoDB, por lo que el backend aparece como `Ready` solo cuando realmente puede hablar con la base de datos.

> **Nota de diseño:** como `/actuator/health` depende de MongoDB, una caída prolongada de la base podría reiniciar el backend. Para mitigarlo, `livenessProbe` usa un umbral alto (`failureThreshold: 6`, `periodSeconds: 15`). En un entorno real se preferirían `/actuator/health/liveness` y `/actuator/health/readiness`, que ya están habilitados en `application.yml`.

---

## 4. API REST del backend

Base: `http://<URL-del-NodePort>`

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| GET | `/actuator/health` | Estado de la app y de MongoDB | `200 {"status":"UP"}` |
| GET | `/api/info` | Pod que responde, configuración pública y total de documentos en MongoDB | `200` |
| GET | `/api/productos` | Lista productos (opcional `?categoria=Vinos`) | `200` |
| GET | `/api/productos/{id}` | Un producto | `200` / `404` |
| POST | `/api/productos` | Crea un producto (escritura en MongoDB) | `201` + `Location` / `400` |
| PUT | `/api/productos/{id}` | Actualiza un producto | `200` / `404` / `400` |
| DELETE | `/api/productos/{id}` | Elimina un producto | `204` / `404` |

Cuerpo de ejemplo (POST/PUT):

```json
{ "nombre": "Vino Malbec Reserva", "categoria": "Vinos", "precio": 59.90, "stock": 120 }
```

---

## 5. Guía de despliegue paso a paso

### 5.1 Requisitos previos

- Docker, Minikube, kubectl y Git instalados.
- Mínimo recomendado para el clúster: 2 CPU y 4 GB de RAM.

### 5.2 Paso a paso

```bash
# 1) Iniciar Minikube
minikube start --driver=docker --cpus=2 --memory=4096
kubectl get nodes                      # el nodo debe estar en estado Ready
minikube addons list | grep storage    # storage-provisioner debe estar enabled

# 2) Clonar el repositorio (reemplazar por la URL real)
git clone https://github.com/<usuario>/<repositorio>.git
cd <repositorio>

# 3) Construir la imagen DENTRO del Docker de Minikube
#    (evita ErrImageNeverPull / ImagePullBackOff, porque Kubernetes usa imagePullPolicy: Never)
eval $(minikube docker-env)
docker build -t backend-webflux:1.0.0 ./src
docker images | grep backend-webflux   # confirma que la imagen existe en el Docker de Minikube

# 4) Aplicar los manifiestos EN ORDEN
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-mongo-pvc.yaml
kubectl apply -f k8s/02-app-configmap.yaml
kubectl apply -f k8s/03-db-secret.yaml
kubectl apply -f k8s/04-mongo-stack.yaml
kubectl apply -f k8s/05-backend-stack.yaml

# 5) Esperar a que los Deployments estén listos
kubectl rollout status deployment/mongo -n practica-s11 --timeout=180s
kubectl rollout status deployment/backend-webflux -n practica-s11 --timeout=240s
```

> **Windows PowerShell:** en lugar de `eval $(minikube docker-env)` usar
> `& minikube -p minikube docker-env --shell powershell | Invoke-Expression`.
>
> **Importante:** `eval $(minikube docker-env)` solo afecta a la terminal actual. Si abres otra terminal debes repetirlo antes de volver a construir la imagen.

### 5.3 Las 4 validaciones exigidas (evidencias para el informe)

**Evidencia 1 — Salud general del clúster**

```bash
kubectl get all,pvc,configmaps,secrets -n practica-s11
```
Esperado: Pods `mongo-*` y `backend-webflux-*` en `1/1 Running`; Services `mongo-service` (ClusterIP) y `backend-service` (NodePort); `persistentvolumeclaim/mongo-pvc` en `Bound`; `configmap/app-config` y `secret/db-secret` presentes.

**Evidencia 2 — Persistencia y volúmenes**

```bash
kubectl describe pvc mongo-pvc -n practica-s11
```
Esperado: `Status: Bound`, `Capacity: 1Gi`, `Access Modes: RWO`, `Volume: pvc-...` y, en `Used By`, el Pod `mongo-...`.

**Evidencia 3 — Inyección de seguridad**

```bash
BACKEND_POD=$(kubectl get pod -n practica-s11 -l app=backend-webflux -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $BACKEND_POD -n practica-s11 -- env
# Versión filtrada (opcional, más legible en la captura):
kubectl exec -it $BACKEND_POD -n practica-s11 -- env | grep -E 'DB_|APP_PORT|MONGO_INITDB'
```
Esperado: `DB_HOST=mongo-service`, `DB_PORT=27017`, `DB_NAME=inventario_db`, `APP_PORT=8080` y las dos variables `MONGO_INITDB_ROOT_*` ya **decodificadas** por Kubernetes.

**Evidencia 4 — Conectividad multi-componente (NodePort)**

```bash
# Obtener la URL externa (con el driver docker en Mac/Windows, dejar esta terminal abierta)
minikube service backend-service -n practica-s11 --url
# Supongamos que devuelve http://127.0.0.1:XXXXX
URL=http://127.0.0.1:XXXXX

curl -i $URL/actuator/health
curl -s $URL/api/info
curl -s -X POST $URL/api/productos \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Vino Malbec Reserva","categoria":"Vinos","precio":59.90,"stock":120}'
curl -s $URL/api/productos
```
Esperado: `/actuator/health` → `{"status":"UP"}`; el POST responde `201` con el `id` generado; el GET lista el documento guardado; `/api/info` muestra el nombre del Pod y `totalProductosEnMongo` ≥ 1.

### 5.4 Prueba extra: la persistencia sobrevive a la muerte del Pod

```bash
kubectl delete pod -l app=mongo -n practica-s11    # elimina el Pod de MongoDB
kubectl get pods -n practica-s11 -w                # Kubernetes crea uno nuevo (Ctrl+C al verlo Running)
curl -s $URL/api/productos                         # el documento sigue existiendo
```
Si el producto creado antes sigue apareciendo, queda demostrado que el PVC conserva los datos.

### 5.5 Limpieza

```bash
kubectl delete namespace practica-s11     # elimina todos los recursos del proyecto
minikube stop                             # detiene el clúster (los datos del PV se conservan)
```

---

## 6. Troubleshooting

| Síntoma | Causa probable | Solución |
|---|---|---|
| `ErrImageNeverPull` o `ImagePullBackOff` en `backend-webflux` | La imagen se construyó en el Docker del host y no en el de Minikube | `eval $(minikube docker-env)` y repetir `docker build -t backend-webflux:1.0.0 ./src`; luego `kubectl rollout restart deployment/backend-webflux -n practica-s11` |
| PVC `Pending` | El provisionador de almacenamiento no está activo | `minikube addons enable storage-provisioner` y `minikube addons enable default-storageclass`; revisar `kubectl describe pvc mongo-pvc -n practica-s11` |
| `mongo` en `CrashLoopBackOff` | Directorio de datos corrupto o con datos de otra inicialización; recursos insuficientes | `kubectl logs deploy/mongo -n practica-s11`; si es un entorno de pruebas, borrar PVC y Deployment y reaplicar |
| Backend `0/1 Running` (no Ready) | MongoDB aún inicia, o falla la autenticación | `kubectl logs deploy/backend-webflux -n practica-s11`; esperar a que `mongo` esté Ready; revisar `Authentication failed` |
| `Authentication failed` en los logs | Credenciales distintas a las usadas al crear el usuario root | Las variables `MONGO_INITDB_*` **solo se aplican la primera vez** que `/data/db` está vacío. Si cambiaste el Secret, elimina Deployment y PVC de Mongo y reaplica 01 y 04 (se pierden los datos) |
| Error al decodificar el Secret o contraseña "incorrecta" | Se codificó con `echo` sin `-n` (incluye `\n`) | Regenerar con `echo -n 'valor' \| base64` y reaplicar `03-db-secret.yaml` |
| Cambié el ConfigMap/Secret y no se nota | Las variables de entorno se leen al crear el Pod | `kubectl rollout restart deployment/backend-webflux -n practica-s11` |
| `Readiness probe failed` / backend se reinicia | La JVM aún arranca o Mongo no responde | `kubectl describe pod <pod> -n practica-s11` (sección Events); aumentar `initialDelaySeconds` si el equipo es lento |
| `OOMKilled` | Límite de memoria muy bajo para la JVM | Aumentar `limits.memory` en `05-backend-stack.yaml` o dar más RAM a Minikube |
| `minikube service` no abre o no responde | Con el driver docker en Mac/Windows el túnel requiere la terminal abierta | Mantener la terminal abierta y usar la URL mostrada; alternativa: `kubectl port-forward svc/backend-service 8080:8080 -n practica-s11` y usar `http://localhost:8080` |
| `kubectl exec ... env` muestra variables viejas | Se está entrando a un Pod anterior | Volver a obtener el nombre con `kubectl get pods -n practica-s11` |
| El comando `docker build` falla descargando dependencias | Sin red o proxy en la terminal | Verificar conexión; reintentar el build |

Comandos de diagnóstico útiles:

```bash
kubectl get events -n practica-s11 --sort-by=.metadata.creationTimestamp
kubectl describe pod -l app=backend-webflux -n practica-s11
kubectl logs -f deploy/backend-webflux -n practica-s11
kubectl get endpoints -n practica-s11          # confirma que los Services tienen Pods detrás
```

