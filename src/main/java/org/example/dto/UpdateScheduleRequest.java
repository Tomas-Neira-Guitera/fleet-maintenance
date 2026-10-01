package org.example.dto;

/**
 * Body de PATCH /api/maintenance-schedule/{id} -- campos parciales. Reprogramar (scheduledAt),
 * editar (title solo si es manual, notes; "" las borra) y cancelar/marcar realizado (status).
 */
public record UpdateScheduleRequest(
        String scheduledAt,
        String status,
        // CAM-77: confirma cancelar también la OT en curso de la programación.
        Boolean cancelWorkOrder,
        String title,
        String notes
) {
    public UpdateScheduleRequest(String scheduledAt, String status, Boolean cancelWorkOrder) {
        this(scheduledAt, status, cancelWorkOrder, null, null);
    }
}
