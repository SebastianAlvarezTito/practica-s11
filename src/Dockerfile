# =============================================================================
# Dockerfile multi-stage del backend Spring WebFlux (Práctica S11 | AP8)
# Contexto de construcción: la carpeta src/  ->  docker build -t backend-webflux:1.0.0 ./src
# Etapa 1 compila con Maven + JDK; etapa 2 ejecuta SOLO el JAR sobre un JRE ligero.
# =============================================================================

# ---------- ETAPA 1: compilación ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
# Directorio de trabajo dentro de la etapa de compilación.
WORKDIR /workspace
# Se copia primero solo el pom.xml: la capa de dependencias se cachea y no se descarga en cada cambio de código.
COPY pom.xml .
# Descarga las dependencias en modo no interactivo (-B) y silencioso (-q).
RUN mvn -B -q dependency:go-offline
# Ahora se copia el código fuente (lo que más cambia, al final para aprovechar la caché).
COPY src ./src
# Compila y empaqueta el JAR sin ejecutar pruebas. Resultado: /workspace/target/s11-backend.jar
RUN mvn -B -q clean package -DskipTests

# ---------- ETAPA 2: ejecución ----------
FROM eclipse-temurin:17-jre
# Se crea un usuario sin privilegios: el proceso NO corre como root (buena práctica de seguridad).
RUN groupadd --system spring && useradd --system --gid spring --uid 1001 spring
# Directorio de la aplicación.
WORKDIR /app
# Copia únicamente el JAR desde la etapa de compilación (la imagen final no incluye Maven ni el JDK).
COPY --from=build /workspace/target/s11-backend.jar /app/app.jar
# Cambia al usuario sin privilegios.
USER spring:spring
# Documenta el puerto por defecto (el real lo define APP_PORT del ConfigMap).
EXPOSE 8080
# MaxRAMPercentage=75: la JVM respeta el límite de memoria del contenedor (768Mi en el Deployment).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
