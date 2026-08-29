package org.example;

import com.sun.net.httpserver.HttpServer;
import org.example.controller.DefectoController;
import org.example.controller.HealthController;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/health", HealthController::handle);
        server.createContext("/api/defectos", DefectoController::handle);

        server.setExecutor(null); // executor por defecto
        server.start();

        System.out.println("Servidor levantado en http://localhost:" + PORT);
        System.out.println("Probá en Postman: GET http://localhost:" + PORT + "/api/health");
    }
}
