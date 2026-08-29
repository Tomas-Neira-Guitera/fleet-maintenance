package org.example.controller;

import com.sun.net.httpserver.HttpExchange;
import org.example.DatabaseConnection;
import org.example.model.HealthStatus;
import org.example.util.HttpResponses;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class HealthController {

    public static void handle(HttpExchange exchange) throws IOException {
        HealthStatus status;

        try (Connection conn = DatabaseConnection.getConnection()) {
            status = HealthStatus.up(conn.getCatalog());
        } catch (SQLException e) {
            status = HealthStatus.down(e.getMessage() == null ? "error desconocido" : e.getMessage());
        }

        HttpResponses.sendJson(exchange, status.statusCode(), status.toJson());
    }
}
