package org.fleetguard.entity;

import java.util.Arrays;

/** Qué originó un aviso (CAM-96). Qué se puede configurar de cada uno está en NotificationCatalog (CAM-117). */
public enum NotificationType {
    WORK_ORDER_ASSIGNED("work_order_assigned"),
    WORK_ORDER_UNASSIGNED("work_order_unassigned"),
    WORK_ORDER_CANCELLED("work_order_cancelled"),
    TRIP_OPEN_REMINDER("trip_open_reminder"),
    BLOCKING_DEFECT_REPORTED("blocking_defect_reported"),
    WORK_ORDER_FINALIZED("work_order_finalized"),
    MAINTENANCE_DUE_SOON("maintenance_due_soon"),
    MAINTENANCE_OVERDUE("maintenance_overdue"),
    RECURRING_DEFECT("recurring_defect"),
    DOCUMENT_DUE_SOON("document_due_soon"),
    DOCUMENT_EXPIRED("document_expired"),
    VEHICLE_UNAVAILABLE("vehicle_unavailable"),
    VEHICLE_AVAILABLE("vehicle_available");

    private final String json;

    NotificationType(String json) {
        this.json = json;
    }

    public static NotificationType fromJson(String value) {
        return Arrays.stream(values()).filter(t -> t.json.equals(value)).findFirst().orElse(null);
    }

    public String toJson() {
        return json;
    }
}
