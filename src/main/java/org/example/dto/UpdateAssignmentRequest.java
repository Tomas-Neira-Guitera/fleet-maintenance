package org.example.dto;

/**
 * Body de PATCH /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}.
 * Corrección administrativa -- a diferencia de un completion, no cuenta como "se hizo el
 * mantenimiento". Campos ausentes (null) no se modifican.
 */
public record UpdateAssignmentRequest(
        Boolean active,
        Long lastDoneKm,
        String lastDoneDate
) {
}
