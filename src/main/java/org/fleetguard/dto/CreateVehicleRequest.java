package org.fleetguard.dto;

/**
 * Body de POST /api/vehicles -- CAM-25. plate/brand/model obligatorios, el resto opcional.
 * checklistTemplateId (CAM-150): plantilla cuyos controles se copian al vehículo nuevo.
 */
public record CreateVehicleRequest(
        String plate,
        String brand,
        String model,
        String vehicleType,
        Integer year,
        String chassisNumber,
        Long odometerKm,
        String checklistTemplateId
) {
    public CreateVehicleRequest(String plate, String brand, String model, String vehicleType, Integer year,
                                String chassisNumber, Long odometerKm) {
        this(plate, brand, model, vehicleType, year, chassisNumber, odometerKm, null);
    }
}
