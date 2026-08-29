package org.example.model;

import org.example.util.Json;

import java.time.Instant;

public class Defecto {

    private final int id;
    private final String gravedad;
    private final Instant fecha;
    private final String descripcion;
    private final String patente;

    public Defecto(int id, String gravedad, Instant fecha, String descripcion, String patente) {
        this.id = id;
        this.gravedad = gravedad;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.patente = patente;
    }

    public String toJson() {
        return String.format(
                "{\"id\":%d,\"gravedad\":\"%s\",\"fecha\":\"%s\",\"descripcion\":\"%s\",\"patente\":\"%s\"}",
                id, Json.escape(gravedad), fecha, Json.escape(descripcion), Json.escape(patente));
    }
}
