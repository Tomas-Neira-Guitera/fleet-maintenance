package org.example.entity;

/** Estado de una orden de trabajo -- ver claude/CAM-14-ordenes-de-trabajo.md en el Project. */
public enum WorkOrderStatus {
    ASIGNADA,
    EN_PROCESO,
    FINALIZADA,
    CANCELADA;

    public static WorkOrderStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "asignada" -> ASIGNADA;
            case "en_proceso" -> EN_PROCESO;
            case "finalizada" -> FINALIZADA;
            case "cancelada" -> CANCELADA;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case ASIGNADA -> "asignada";
            case EN_PROCESO -> "en_proceso";
            case FINALIZADA -> "finalizada";
            case CANCELADA -> "cancelada";
        };
    }
}
