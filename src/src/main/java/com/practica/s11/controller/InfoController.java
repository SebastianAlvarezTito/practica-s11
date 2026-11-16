package com.practica.s11.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.practica.s11.service.ProductoService;

import reactor.core.publisher.Mono;

/**
 * Endpoint de diagnóstico para la evidencia de conectividad multi-componente.
 * Muestra qué Pod respondió, con qué configuración (ConfigMap) y cuántos documentos hay en MongoDB.
 * Nunca devuelve credenciales.
 */
@RestController
@RequestMapping("/api/info")
public class InfoController {

    private final ProductoService servicio;
    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String appPort;

    /**
     * Constructor con inyección de dependencias y de valores de configuración.
     *
     * @param servicio servicio de productos (se usa para contar documentos)
     * @param dbHost   valor de DB_HOST (ConfigMap)
     * @param dbPort   valor de DB_PORT (ConfigMap)
     * @param dbName   valor de DB_NAME (ConfigMap)
     * @param appPort  valor de APP_PORT (ConfigMap)
     */
    public InfoController(ProductoService servicio,
                          @Value("${DB_HOST:no-definido}") String dbHost,
                          @Value("${DB_PORT:no-definido}") String dbPort,
                          @Value("${DB_NAME:no-definido}") String dbName,
                          @Value("${APP_PORT:no-definido}") String appPort) {
        this.servicio = servicio;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.appPort = appPort;
    }

    /**
     * GET /api/info
     * Devuelve el nombre del Pod, la configuración pública y el total de productos almacenados en MongoDB.
     */
    @GetMapping
    public Mono<Map<String, Object>> informacion() {
        // HOSTNAME dentro de un Pod coincide con el nombre del Pod.
        String pod = System.getenv().getOrDefault("HOSTNAME", "desconocido");
        return servicio.contar().map(total -> {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("aplicacion", "s11-backend-webflux");
            cuerpo.put("pod", pod);
            cuerpo.put("dbHost", dbHost);
            cuerpo.put("dbPort", dbPort);
            cuerpo.put("dbName", dbName);
            cuerpo.put("appPort", appPort);
            cuerpo.put("totalProductosEnMongo", total);
            cuerpo.put("timestamp", Instant.now().toString());
            return cuerpo;
        });
    }
}
