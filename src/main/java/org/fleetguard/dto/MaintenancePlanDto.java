package org.fleetguard.dto;

/** Coincide con un item de GET /api/maintenance-plans -- ver CAM-40-maintenance-api-contract.md. */
public record MaintenancePlanDto(
        String id,
        String name,
        String category,
        String intervalType,
        Integer intervalKm,
        Integer intervalDays,
        boolean active,
        // CAM-127: el documento que renueva este plan ("vtv", "seguro"…), o null.
        String documentType
) {
}
