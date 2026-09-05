package org.example.dto;

/** Body de POST /api/maintenance-plans. */
public record CreateMaintenancePlanRequest(
        String name,
        String category,
        String intervalType,
        Integer intervalKm,
        Integer intervalDays
) {
}
