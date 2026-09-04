package org.example.dto;

/** Body de PATCH /api/maintenance-plans/{id} -- campos ausentes (null) no se modifican. */
public record UpdateMaintenancePlanRequest(
        String name,
        String category,
        String intervalType,
        Integer intervalKm,
        Integer intervalDays,
        Boolean active
) {
}
