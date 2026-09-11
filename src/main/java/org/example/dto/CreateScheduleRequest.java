package org.example.dto;

/**
 * Body de POST /api/maintenance-schedule -- ver CAM-42-programacion-mantenimientos.md.
 * sourceId es obligatorio si sourceType es assignment/defect; vehicleId+title son
 * obligatorios si sourceType es manual (no hay plan ni defecto del que resolverlos).
 */
public record CreateScheduleRequest(
        String sourceType,
        String sourceId,
        String vehicleId,
        String title,
        String scheduledAt,
        String notes
) {
}
