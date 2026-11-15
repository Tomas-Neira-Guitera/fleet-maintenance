package org.fleetguard.entity;

import java.util.List;

/** Estado de una orden de trabajo -- ver claude/CAM-14-ordenes-de-trabajo.md en el Project. */
public enum WorkOrderStatus {
    ASIGNADA,
    EN_PROCESO,
    /** CAM-148: pausada con un motivo (esperando un repuesto, al proveedor...). Sigue abierta. */
    EN_ESPERA,
    FINALIZADA,
    CANCELADA;

    /**
     * Los estados de una OT abierta: la que todavía se puede editar, que impide abrir otra para el
     * mismo origen y que cuenta como trabajo pendiente. Un solo lugar, para que un estado nuevo no
     * quede afuera de alguna consulta (CAM-148).
     */
    public static final List<WorkOrderStatus> OPEN = List.of(ASIGNADA, EN_PROCESO, EN_ESPERA);

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    /** Ya se empezó a trabajar: en proceso o pausada (CAM-148). */
    public boolean isStarted() {
        return this == EN_PROCESO || this == EN_ESPERA;
    }

    public static WorkOrderStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "asignada" -> ASIGNADA;
            case "en_proceso" -> EN_PROCESO;
            case "en_espera" -> EN_ESPERA;
            case "finalizada" -> FINALIZADA;
            case "cancelada" -> CANCELADA;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case ASIGNADA -> "asignada";
            case EN_PROCESO -> "en_proceso";
            case EN_ESPERA -> "en_espera";
            case FINALIZADA -> "finalizada";
            case CANCELADA -> "cancelada";
        };
    }
}
