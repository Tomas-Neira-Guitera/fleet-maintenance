package org.fleetguard.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/downtime (CAM-134): disponibilidad y tiempo de reparación en un período. */
public record DowntimeDto(Instant from, Instant to, Fleet fleet, List<Vehicle> vehicles) {

    /** Los totales de los vehículos de la respuesta. availabilityPct es null si el período todavía no empezó. */
    public record Fleet(Double availabilityPct, double downtimeHours, int stops, RepairTimes repairs) {
    }

    public record Vehicle(String vehicleId, String plate, Double availabilityPct, double downtimeHours, int stops,
                          RepairTimes repairs) {
    }

    /** Promedio de horas entre la creación y la finalización de las OTs; avgHours es null si no hay ninguna. */
    public record RepairTimes(Double avgHours, int count, Average internal, Average external) {
    }

    public record Average(Double avgHours, int count) {
    }
}
