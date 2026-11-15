package org.fleetguard.entity;

public enum InspectionType {
    PRE_TRIP,
    POST_TRIP,
    /**
     * CAM-139: un defecto que el chofer reporta durante el viaje, sin cerrarlo. Se guarda como una
     * inspección de una sola respuesta dentro del viaje abierto, para que el defecto llegue al vehículo,
     * al chofer y al viaje igual que los demás. No es una inspección para el chofer: no se envía por
     * POST /inspections ({@link #fromJson} no lo acepta), no se cuenta en su actividad y no aparece
     * como inspección en el historial.
     */
    ON_ROAD;

    /** Solo los dos tipos que se envían por POST /inspections; "on-road" no se acepta ahí. */
    public static InspectionType fromJson(String value) {
        if ("pre-trip".equals(value)) {
            return PRE_TRIP;
        }
        if ("post-trip".equals(value)) {
            return POST_TRIP;
        }
        return null;
    }

    public String toJson() {
        return switch (this) {
            case PRE_TRIP -> "pre-trip";
            case POST_TRIP -> "post-trip";
            case ON_ROAD -> "on-road";
        };
    }
}
