package org.fleetguard.entity;

/** Categoría de un gasto operativo del vehículo -- CAM-109. */
public enum VehicleExpenseCategory {
    COMBUSTIBLE,
    SEGURO,
    PATENTE,
    VTV,
    PEAJES,
    OTRO;

    public static VehicleExpenseCategory fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "combustible" -> COMBUSTIBLE;
            case "seguro" -> SEGURO;
            case "patente" -> PATENTE;
            case "vtv" -> VTV;
            case "peajes" -> PEAJES;
            case "otro" -> OTRO;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case COMBUSTIBLE -> "combustible";
            case SEGURO -> "seguro";
            case PATENTE -> "patente";
            case VTV -> "vtv";
            case PEAJES -> "peajes";
            case OTRO -> "otro";
        };
    }
}
