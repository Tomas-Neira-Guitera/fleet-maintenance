package org.fleetguard.dto;

/** Body de POST .../completions. */
public record CreateCompletionRequest(
        String completedAt,
        Long completedKm,
        String workOrderId,
        String notes,
        // CAM-127: el documento renovado, solo si el plan renueva uno (VTV, seguro…). Opcional.
        DocumentInputDto document
) {
}
