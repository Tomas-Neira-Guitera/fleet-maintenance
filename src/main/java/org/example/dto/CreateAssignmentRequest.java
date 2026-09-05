package org.example.dto;

/** Body de POST /api/vehicles/{vehicleId}/maintenance-assignments. */
public record CreateAssignmentRequest(
        String maintenancePlanId,
        Long lastDoneKm,
        String lastDoneDate
) {
}
