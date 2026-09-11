package org.example.entity;

/** Estado de un mantenimiento programado -- ver CAM-42-programacion-mantenimientos.md. */
public enum ScheduleStatus {
    SCHEDULED,
    DONE,
    CANCELLED;

    public static ScheduleStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "scheduled" -> SCHEDULED;
            case "done" -> DONE;
            case "cancelled" -> CANCELLED;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case SCHEDULED -> "scheduled";
            case DONE -> "done";
            case CANCELLED -> "cancelled";
        };
    }
}
