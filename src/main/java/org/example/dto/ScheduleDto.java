package org.example.dto;

/** Coincide con un item de GET /api/maintenance-schedule -- ver CAM-42-programacion-mantenimientos.md. */
public record ScheduleDto(
        String id,
        String vehicleId,
        String plate,
        String sourceType,
        String assignmentId,
        String defectId,
        String title,
        String scheduledAt,
        String status,
        String notes
) {
}
