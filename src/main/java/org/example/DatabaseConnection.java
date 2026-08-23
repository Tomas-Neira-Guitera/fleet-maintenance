package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Utilidad para obtener una conexión JDBC a la base de datos Postgres "TIP".
 *
 * Lee los datos de conexión desde src/main/resources/db.properties (ese
 * archivo NO se sube a git, ver .gitignore). Si no existe en tu máquina,
 * copiá db.properties.example a db.properties y completá tu password.
 */
public class DatabaseConnection {

    private static final String PROPERTIES_FILE = "/db.properties";

    public static Connection getConnection() throws SQLException {
        Properties props = loadProperties();

        String host = props.getProperty("db.host", "localhost");
        String port = props.getProperty("db.port", "5432");
        String dbName = props.getProperty("db.name", "TIP");
        String user = props.getProperty("db.user", "postgres");
        String password = props.getProperty("db.password", "");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);

        return DriverManager.getConnection(url, user, password);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = DatabaseConnection.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "No se encontró src/main/resources/db.properties. " +
                        "Copiá db.properties.example a db.properties y completá tus datos.");
            }
            props.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Error leyendo db.properties", e);
        }
        return props;
    }
}
