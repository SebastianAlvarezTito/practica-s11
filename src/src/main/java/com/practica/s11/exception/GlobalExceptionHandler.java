package com.practica.s11.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import reactor.core.publisher.Mono;

/**
 * Manejador global de errores de validación: convierte las violaciones de @Valid
 * en una respuesta 400 con un JSON legible indicando qué campo falló y por qué.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja los errores de validación del cuerpo de la petición.
     *
     * @param excepcion excepción lanzada por Spring WebFlux al fallar la validación
     * @return respuesta 400 con el detalle de errores por campo
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> manejarValidacion(WebExchangeBindException excepcion) {
        Map<String, String> detalles = new LinkedHashMap<>();
        excepcion.getFieldErrors()
                .forEach(error -> detalles.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", 400);
        cuerpo.put("error", "Solicitud inválida");
        cuerpo.put("detalles", detalles);
        return Mono.just(ResponseEntity.badRequest().body(cuerpo));
    }
}
