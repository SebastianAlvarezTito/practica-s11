package com.practica.s11;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal del backend reactivo de la Práctica S11 | AP8.
 *
 * <p>@SpringBootApplication activa la configuración automática, el escaneo de componentes
 * (controladores, servicios, repositorios) y el servidor reactivo Netty de WebFlux.</p>
 */
@SpringBootApplication
public class PracticaS11Application {

    /**
     * Punto de entrada de la aplicación.
     *
     * @param args argumentos de línea de comandos (no se utilizan)
     */
    public static void main(String[] args) {
        SpringApplication.run(PracticaS11Application.class, args);
    }
}
