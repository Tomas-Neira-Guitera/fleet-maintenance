package org.example.dto;

/**
 * Body de POST /api/work-orders -- ver claude/CAM-14-ordenes-de-trabajo.md. sourceId es
 * obligatorio si sourceType es scheduled_maintenance/defect; vehicleId+title son
 * obligatorios si sourceType es manual (no hay de dónde resolverlos).
 */
public record CreateWorkOrderRequest(
        String sourceType,
        String sourceId,
        String vehicleId,
        String title,
        String description,
        String executionType,
        String externalProvider,
        String assignee,
        String technicianId
) {
}
