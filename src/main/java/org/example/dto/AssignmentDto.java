package org.example.dto;

/** Coincide con un item de GET /api/vehicles/{vehicleId}/maintenance-assignments. */
public record AssignmentDto(
        String id,
        String vehicleId,
        String maintenancePlanId,
        String planName,
        String intervalType,
        Long lastDoneKm,
        String lastDoneDate,
        Long nextDueKm,
        String nextDueDate,
        String status,
        boolean active
) {
}
