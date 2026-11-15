package org.fleetguard.dto;

import java.util.List;

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
        NextMaintenanceDto nextMaintenance,
        // CAM-127: si no puede salir y por qué (defecto bloqueante o documento vencido).
        boolean blocked,
        List<BlockReasonDto> blockReasons
) {
}
