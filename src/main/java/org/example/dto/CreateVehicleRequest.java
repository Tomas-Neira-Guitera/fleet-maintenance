package org.example.dto;

/** Body de POST /api/vehicles -- CAM-25. plate/brand/model obligatorios, el resto opcional. */
public record CreateVehicleRequest(
        String plate,
        String brand,
        String model,
        String vehicleType,
        Integer year,
        String chassisNumber,
        Long odometerKm
) {
}
