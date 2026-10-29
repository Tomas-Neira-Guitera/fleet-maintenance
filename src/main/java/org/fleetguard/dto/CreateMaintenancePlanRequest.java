package org.fleetguard.dto;

/** Body de POST /api/maintenance-plans. */
public record CreateMaintenancePlanRequest(
        String name,
        String category,
        String intervalType,
        Integer intervalKm,
        Integer intervalDays,
        // CAM-127: opcional, solo en planes por tiempo.
        String documentType
) {
}
