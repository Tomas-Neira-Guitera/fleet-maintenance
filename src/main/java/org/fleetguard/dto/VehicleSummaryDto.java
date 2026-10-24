package org.fleetguard.dto;

/** Coincide con components.schemas.VehicleSummary de openapi.yaml. */
public record VehicleSummaryDto(
        String id,
        String plate,
        String brand,
        String model,
        String status,
        String vehicleType,
        Integer year,
        String chassisNumber,
        long odometerKm,
        boolean active,
        // CAM-89: true si tiene un defecto bloqueante sin resolver (no puede iniciar un viaje).
        boolean blocked,
        // CAM-87: el viaje abierto, si status es "on-trip"; null si está disponible.
        CurrentTripDto currentTrip
) {
}
