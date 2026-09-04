package org.example.dto;

/** Coincide con un item de GET /api/vehicles?view=fleet-status -- ver CAM-40-maintenance-api-contract.md. */
public record FleetStatusRowDto(
        String vehicleId,
        String plate,
        String brand,
        String model,
        String vehicleType,
        long odometerKm,
        int healthScore,
        String status,
        NextMaintenanceDto nextMaintenance
) {
}
