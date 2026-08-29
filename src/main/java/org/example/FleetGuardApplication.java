package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the FleetGuard backend (Spring Boot).
 * <p>
 * Replaces the previous plain {@code com.sun.net.httpserver.HttpServer} setup
 * in {@code Main.java} / {@code DatabaseConnection.java} -- the project now
 * uses Spring MVC + Spring Data JPA instead of a hand-rolled HTTP server and
 * raw JDBC.
 */
@SpringBootApplication
public class FleetGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetGuardApplication.class, args);
    }
}
