package com.practica.s11.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.practica.s11.model.Producto;

import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo de productos.
 * ReactiveMongoRepository ya provee save, findById, findAll, delete y count devolviendo Mono/Flux,
 * por lo que ninguna operación bloquea el hilo del servidor.
 */
@Repository
public interface ProductoRepository extends ReactiveMongoRepository<Producto, String> {

    /**
     * Busca productos por categoría ignorando mayúsculas y minúsculas.
     * Spring Data deriva la consulta automáticamente a partir del nombre del método.
     *
     * @param categoria categoría a buscar
     * @return flujo con los productos de esa categoría
     */
    Flux<Producto> findByCategoriaIgnoreCase(String categoria);
}
