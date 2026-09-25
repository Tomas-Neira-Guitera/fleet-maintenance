package org.example.entity;

/** CAM-62 -- si el trabajo lo resuelve personal propio (taller interno) o se lleva afuera. */
public enum WorkOrderExecutionType {
    INTERNO,
    EXTERNO;

    public static WorkOrderExecutionType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "interno" -> INTERNO;
            case "externo" -> EXTERNO;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case INTERNO -> "interno";
            case EXTERNO -> "externo";
        };
    }
}
