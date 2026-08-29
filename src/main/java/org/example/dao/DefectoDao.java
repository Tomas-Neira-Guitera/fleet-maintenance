package org.example.dao;

import org.example.DatabaseConnection;
import org.example.model.Defecto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DefectoDao {

    public List<Defecto> listarOrdenadosPorGravedadYFecha() throws SQLException {
        String query = "SELECT id, gravedad, fecha, descripcion, patente FROM defectos " +
                "ORDER BY CASE gravedad WHEN 'alto' THEN 3 WHEN 'medio' THEN 2 WHEN 'bajo' THEN 1 END DESC, " +
                "fecha DESC";

        List<Defecto> defectos = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                defectos.add(new Defecto(
                        rs.getInt("id"),
                        rs.getString("gravedad"),
                        rs.getTimestamp("fecha").toInstant(),
                        rs.getString("descripcion"),
                        rs.getString("patente")));
            }
        }

        return defectos;
    }
}
