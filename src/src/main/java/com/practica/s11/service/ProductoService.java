package com.practica.s11.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.practica.s11.model.Producto;
import com.practica.s11.model.ProductoRequest;
import com.practica.s11.repository.ProductoRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lógica de negocio de productos. Todas las operaciones son reactivas: devuelven Mono (0..1 elemento)
 * o Flux (0..N elementos) y se ejecutan solo cuando alguien se suscribe (el framework lo hace por nosotros).
 */
@Service
public class ProductoService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductoService.class);

    private final ProductoRepository repositorio;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param repositorio repositorio reactivo de MongoDB
     */
    public ProductoService(ProductoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Lista los productos, opcionalmente filtrados por categoría.
     *
     * @param categoria categoría a filtrar; si es nula o vacía se devuelven todos
     * @return flujo de productos
     */
    public Flux<Producto> listar(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return repositorio.findAll();
        }
        return repositorio.findByCategoriaIgnoreCase(categoria.trim());
    }

    /**
     * Obtiene un producto por su identificador.
     *
     * @param id identificador del producto
     * @return Mono con el producto, o vacío si no existe
     */
    public Mono<Producto> obtenerPorId(String id) {
        return repositorio.findById(id);
    }

    /**
     * Crea un nuevo producto y lo persiste en MongoDB.
     *
     * @param solicitud datos validados enviados por el cliente
     * @return Mono con el producto guardado (incluye el id generado)
     */
    public Mono<Producto> crear(ProductoRequest solicitud) {
        Producto producto = new Producto();
        producto.setNombre(solicitud.nombre().trim());
        producto.setCategoria(solicitud.categoria().trim());
        producto.setPrecio(solicitud.precio());
        producto.setStock(solicitud.stock());
        Instant ahora = Instant.now();
        producto.setCreadoEn(ahora);
        producto.setActualizadoEn(ahora);
        return repositorio.save(producto)
                .doOnSuccess(guardado -> LOG.info("Producto creado con id {}", guardado.getId()));
    }

    /**
     * Actualiza un producto existente.
     *
     * @param id        identificador del producto a modificar
     * @param solicitud nuevos datos validados
     * @return Mono con el producto actualizado, o vacío si el id no existe
     */
    public Mono<Producto> actualizar(String id, ProductoRequest solicitud) {
        return repositorio.findById(id)
                .flatMap(existente -> {
                    existente.setNombre(solicitud.nombre().trim());
                    existente.setCategoria(solicitud.categoria().trim());
                    existente.setPrecio(solicitud.precio());
                    existente.setStock(solicitud.stock());
                    existente.setActualizadoEn(Instant.now());
                    return repositorio.save(existente);
                });
    }

    /**
     * Elimina un producto por su identificador.
     *
     * @param id identificador del producto
     * @return Mono con true si se eliminó, o false si no existía
     */
    public Mono<Boolean> eliminar(String id) {
        return repositorio.findById(id)
                .flatMap(existente -> repositorio.delete(existente).thenReturn(Boolean.TRUE))
                .defaultIfEmpty(Boolean.FALSE);
    }

    /**
     * Cuenta los documentos de la colección (prueba de lectura real desde MongoDB).
     *
     * @return Mono con el total de productos
     */
    public Mono<Long> contar() {
        return repositorio.count();
    }
}
