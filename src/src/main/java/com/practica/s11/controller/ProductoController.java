package com.practica.s11.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.practica.s11.model.Producto;
import com.practica.s11.model.ProductoRequest;
import com.practica.s11.service.ProductoService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API REST reactiva de productos: demuestra lectura (GET) y escritura (POST, PUT, DELETE) en MongoDB.
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService servicio;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param servicio servicio de negocio de productos
     */
    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    /**
     * GET /api/productos  (opcional: ?categoria=Vinos)
     * Lee todos los productos desde MongoDB y los emite como flujo JSON.
     */
    @GetMapping
    public Flux<Producto> listar(@RequestParam(name = "categoria", required = false) String categoria) {
        return servicio.listar(categoria);
    }

    /**
     * GET /api/productos/{id}
     * Devuelve 200 con el producto o 404 si no existe.
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Producto>> obtener(@PathVariable String id) {
        return servicio.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/productos
     * Valida el cuerpo JSON, escribe el documento en MongoDB y responde 201 Created con la cabecera Location.
     */
    @PostMapping
    public Mono<ResponseEntity<Producto>> crear(@Valid @RequestBody ProductoRequest solicitud) {
        return servicio.crear(solicitud)
                .map(guardado -> ResponseEntity
                        .created(URI.create("/api/productos/" + guardado.getId()))
                        .body(guardado));
    }

    /**
     * PUT /api/productos/{id}
     * Reemplaza los datos del producto. Devuelve 200 con el producto actualizado o 404 si no existe.
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Producto>> actualizar(@PathVariable String id,
                                                      @Valid @RequestBody ProductoRequest solicitud) {
        return servicio.actualizar(id, solicitud)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/productos/{id}
     * Devuelve 204 No Content si se eliminó o 404 si no existía.
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> eliminar(@PathVariable String id) {
        return servicio.eliminar(id)
                .map(eliminado -> eliminado
                        ? ResponseEntity.noContent().<Void>build()
                        : ResponseEntity.notFound().<Void>build());
    }
}
