package com.practica.s11.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Objeto de transferencia (DTO) con los datos que el cliente envía en POST y PUT.
 * Separarlo de la entidad evita que el cliente controle campos internos como id o fechas.
 *
 * @param nombre    nombre del producto (obligatorio, máximo 100 caracteres)
 * @param categoria categoría del producto (obligatoria, máximo 50 caracteres)
 * @param precio    precio unitario (obligatorio, mayor o igual a cero)
 * @param stock     unidades disponibles (obligatorio, mayor o igual a cero)
 */
public record ProductoRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "La categoría es obligatoria")
        @Size(max = 50, message = "La categoría no puede superar los 50 caracteres")
        String categoria,

        @NotNull(message = "El precio es obligatorio")
        @PositiveOrZero(message = "El precio no puede ser negativo")
        Double precio,

        @NotNull(message = "El stock es obligatorio")
        @PositiveOrZero(message = "El stock no puede ser negativo")
        Integer stock) {
}
