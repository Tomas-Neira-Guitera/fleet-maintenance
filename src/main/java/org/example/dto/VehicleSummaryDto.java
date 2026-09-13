package org.example.dto;

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
        boolean active
) {
}
