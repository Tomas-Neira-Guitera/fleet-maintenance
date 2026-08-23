package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/health", Main::handleHealth);

        server.setExecutor(null); // executor por defecto
        server.start();

        System.out.println("Servidor levantado en http://localhost:" + PORT);
        System.out.println("Probá en Postman: GET http://localhost:" + PORT + "/api/health");
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        String body;
        int statusCode;

        try (Connection conn = DatabaseConnection.getConnection()) {
            body = String.format(
                    "{\"status\":\"UP\",\"database\":\"%s\",\"timestamp\":\"%s\"}",
                    conn.getCatalog(), Instant.now());
            statusCode = 200;
        } catch (SQLException e) {
            String errorMessage = e.getMessage() == null ? "error desconocido" : e.getMessage().replace("\"", "'");
            body = String.format("{\"status\":\"DOWN\",\"error\":\"%s\"}", errorMessage);
            statusCode = 500;
        }

        byte[] responseBytes = body.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
