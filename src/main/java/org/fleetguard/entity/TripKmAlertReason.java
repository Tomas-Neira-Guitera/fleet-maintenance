package org.fleetguard.entity;

/** CAM-152: por qué un viaje quedó marcado con un kilometraje fuera de lo normal. */
public enum TripKmAlertReason {
    /** El viaje recorrió más del doble del promedio del vehículo. */
    ABOVE_AVERAGE,
    /** El viaje recorrió menos de la mitad del promedio del vehículo. */
    BELOW_AVERAGE,
    /** El km que declaró el chofer al cerrar es menor al que ya tenía el vehículo. */
    BELOW_VEHICLE_KM;

    public String toJson() {
        return switch (this) {
            case ABOVE_AVERAGE -> "above_average";
            case BELOW_AVERAGE -> "below_average";
            case BELOW_VEHICLE_KM -> "below_vehicle_km";
        };
    }
}
