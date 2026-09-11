package org.example.entity;

/** Origen de un mantenimiento programado -- ver CAM-42-programacion-mantenimientos.md. */
public enum ScheduleSourceType {
    ASSIGNMENT,
    DEFECT,
    /** Programación suelta, sin plan ni defecto de origen -- el cliente manda vehicleId + title directo. */
    MANUAL;

    public static ScheduleSourceType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "assignment" -> ASSIGNMENT;
            case "defect" -> DEFECT;
            case "manual" -> MANUAL;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case ASSIGNMENT -> "assignment";
            case DEFECT -> "defect";
            case MANUAL -> "manual";
        };
    }
}
