package org.example.controller;

import com.sun.net.httpserver.HttpExchange;
import org.example.dao.DefectoDao;
import org.example.model.Defecto;
import org.example.util.HttpResponses;
import org.example.util.Json;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class DefectoController {

    private static final DefectoDao dao = new DefectoDao();

    public static void handle(HttpExchange exchange) throws IOException {
        String body;
        int statusCode;

        try {
            List<Defecto> defectos = dao.listarOrdenadosPorGravedadYFecha();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < defectos.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                json.append(defectos.get(i).toJson());
            }
            json.append("]");

            body = json.toString();
            statusCode = 200;
        } catch (SQLException e) {
            String errorMessage = e.getMessage() == null ? "error desconocido" : e.getMessage();
            body = String.format("{\"error\":\"%s\"}", Json.escape(errorMessage));
            statusCode = 500;
        }

        HttpResponses.sendJson(exchange, statusCode, body);
    }
}
