package org.fleetguard.entity;

/** CAM-148: por qué una orden de trabajo quedó en espera. */
public enum WorkOrderPauseReason {
    ESPERANDO_REPUESTO,
    ESPERANDO_PROVEEDOR,
    ESPERANDO_VEHICULO,
    /** Exige un comentario que diga cuál. */
    OTRO;

    public static WorkOrderPauseReason fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "esperando_repuesto" -> ESPERANDO_REPUESTO;
            case "esperando_proveedor" -> ESPERANDO_PROVEEDOR;
            case "esperando_vehiculo" -> ESPERANDO_VEHICULO;
            case "otro" -> OTRO;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case ESPERANDO_REPUESTO -> "esperando_repuesto";
            case ESPERANDO_PROVEEDOR -> "esperando_proveedor";
            case ESPERANDO_VEHICULO -> "esperando_vehiculo";
            case OTRO -> "otro";
        };
    }
}
