package org.example.model;

import org.example.util.Json;

import java.time.Instant;

public class HealthStatus {

    private final boolean up;
    private final String database;
    private final String error;
    private final Instant timestamp;

    private HealthStatus(boolean up, String database, String error, Instant timestamp) {
        this.up = up;
        this.database = database;
        this.error = error;
        this.timestamp = timestamp;
    }

    public static HealthStatus up(String database) {
        return new HealthStatus(true, database, null, Instant.now());
    }

    public static HealthStatus down(String error) {
        return new HealthStatus(false, null, error, Instant.now());
    }

    public int statusCode() {
        return up ? 200 : 500;
    }

    public String toJson() {
        if (up) {
            return String.format(
                    "{\"status\":\"UP\",\"database\":\"%s\",\"timestamp\":\"%s\"}",
                    Json.escape(database), timestamp);
        }
        return String.format("{\"status\":\"DOWN\",\"error\":\"%s\"}", Json.escape(error));
    }
}
