package com.smartops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del monolito modular. El escaneo desde {@code com.smartops} recoge
 * los componentes de todos los módulos (application, infrastructure, batch, api).
 */
@SpringBootApplication
public class SmartOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartOpsApplication.class, args);
    }
}
