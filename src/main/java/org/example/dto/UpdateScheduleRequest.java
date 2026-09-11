package org.example.dto;

/**
 * Body de PATCH /api/maintenance-schedule/{id} -- campos parciales. Reprogramar
 * (scheduledAt) y cancelar/marcar realizado (status) pueden venir juntos o por separado.
 */
public record UpdateScheduleRequest(
        String scheduledAt,
        String status
) {
}
