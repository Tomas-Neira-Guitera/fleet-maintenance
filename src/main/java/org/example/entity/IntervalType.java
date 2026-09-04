package org.example.entity;

/** Tipo de intervalo de un plan de mantenimiento -- ver CAM-40-modelo-mantenimiento-preventivo.md. */
public enum IntervalType {
    KM,
    TIME,
    BOTH;

    public static IntervalType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "km" -> KM;
            case "time" -> TIME;
            case "both" -> BOTH;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case KM -> "km";
            case TIME -> "time";
            case BOTH -> "both";
        };
    }
}
