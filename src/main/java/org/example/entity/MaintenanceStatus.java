package org.example.entity;

/**
 * Estado de un plan o de un vehículo -- umbral fijo global, ver
 * CAM-40-modelo-mantenimiento-preventivo.md sección 3.
 */
public enum MaintenanceStatus {
    AL_DIA,
    POR_VENCER,
    VENCIDO;

    /** Mayor severidad primero -- usado para "peor estado" y para elegir el plan más urgente. */
    public int severity() {
        return switch (this) {
            case VENCIDO -> 2;
            case POR_VENCER -> 1;
            case AL_DIA -> 0;
        };
    }

    public String toJson() {
        return switch (this) {
            case AL_DIA -> "al_dia";
            case POR_VENCER -> "por_vencer";
            case VENCIDO -> "vencido";
        };
    }
}
