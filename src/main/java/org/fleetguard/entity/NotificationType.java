package org.fleetguard.entity;

/** Qué originó un aviso in-app (CAM-96). Cada card de aviso suma el suyo (CAM-97 a CAM-103). */
public enum NotificationType {
    /** CAM-99: una OT quedó a cargo del técnico. */
    WORK_ORDER_ASSIGNED,
    /** CAM-100: el técnico dejó de estar a cargo de una OT abierta (se reasignó a otro o se desasignó). */
    WORK_ORDER_UNASSIGNED,
    /** CAM-100: se canceló una OT que el técnico tenía a cargo. */
    WORK_ORDER_CANCELLED,
    /** CAM-101: el chofer tiene un viaje abierto hace más de N horas. */
    TRIP_OPEN_REMINDER;

    public static NotificationType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "work_order_assigned" -> WORK_ORDER_ASSIGNED;
            case "work_order_unassigned" -> WORK_ORDER_UNASSIGNED;
            case "work_order_cancelled" -> WORK_ORDER_CANCELLED;
            case "trip_open_reminder" -> TRIP_OPEN_REMINDER;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case WORK_ORDER_ASSIGNED -> "work_order_assigned";
            case WORK_ORDER_UNASSIGNED -> "work_order_unassigned";
            case WORK_ORDER_CANCELLED -> "work_order_cancelled";
            case TRIP_OPEN_REMINDER -> "trip_open_reminder";
        };
    }
}
