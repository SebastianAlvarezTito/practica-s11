package com.practica.s11.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB que representa un producto del inventario.
 * Se almacena en la colección "productos" de la base de datos definida por DB_NAME.
 */
@Document(collection = "productos")
public class Producto {

    /** Identificador único generado por MongoDB (ObjectId representado como String). */
    @Id
    private String id;

    /** Nombre comercial del producto. */
    private String nombre;

    /** Categoría del producto (por ejemplo: Vinos, Licores). */
    private String categoria;

    /** Precio unitario. */
    private Double precio;

    /** Unidades disponibles en inventario. */
    private Integer stock;

    /** Fecha y hora de creación del documento (UTC). */
    private Instant creadoEn;

    /** Fecha y hora de la última modificación (UTC). */
    private Instant actualizadoEn;

    /** Constructor vacío requerido por Spring Data para instanciar el documento. */
    public Producto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
