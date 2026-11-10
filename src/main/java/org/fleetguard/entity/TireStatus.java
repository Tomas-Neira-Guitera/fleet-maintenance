package org.fleetguard.entity;

/** CAM-157: en stock (sin montar), montada en un vehículo o dada de baja. */
public enum TireStatus {
    STOCK, MOUNTED, RETIRED;

    public static TireStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "stock" -> STOCK;
            case "mounted" -> MOUNTED;
            case "retired" -> RETIRED;
            default -> null;
        };
    }

    public String toJson() {
        return name().toLowerCase();
    }
}
