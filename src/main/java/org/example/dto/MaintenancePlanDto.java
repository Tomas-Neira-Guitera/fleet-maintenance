package org.example.dto;

/** Coincide con un item de GET /api/maintenance-plans -- ver CAM-40-maintenance-api-contract.md. */
public record MaintenancePlanDto(
        String id,
        String name,
        String category,
        String intervalType,
        Integer intervalKm,
        Integer intervalDays,
        boolean active
) {
}
