package org.example.entity;

/** Origen de una orden de trabajo -- ver claude/CAM-14-ordenes-de-trabajo.md en el Project. */
public enum WorkOrderSourceType {
    SCHEDULED_MAINTENANCE,
    DEFECT,
    /** OT suelta, sin origen -- el cliente manda vehicleId + title directo. */
    MANUAL;

    public static WorkOrderSourceType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "scheduled_maintenance" -> SCHEDULED_MAINTENANCE;
            case "defect" -> DEFECT;
            case "manual" -> MANUAL;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case SCHEDULED_MAINTENANCE -> "scheduled_maintenance";
            case DEFECT -> "defect";
            case MANUAL -> "manual";
        };
    }
}
