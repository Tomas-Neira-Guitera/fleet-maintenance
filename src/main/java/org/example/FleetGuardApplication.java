package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada del backend de FleetGuard (Spring Boot). */
@SpringBootApplication
public class FleetGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetGuardApplication.class, args);
    }
}
