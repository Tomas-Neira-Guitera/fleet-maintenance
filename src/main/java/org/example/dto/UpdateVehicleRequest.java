package org.example.dto;

/** Body de PATCH /api/vehicles/{id} -- CAM-25. Campos ausentes (null) no se modifican. */
public record UpdateVehicleRequest(
        String plate,
        String brand,
        String model,
        String vehicleType,
        Integer year,
        String chassisNumber,
        Boolean active
) {
}
