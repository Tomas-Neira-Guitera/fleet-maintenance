package org.example.dto;

/** Coincide con components.schemas.MaintenanceHistoryItem de openapi.yaml. */
public record MaintenanceHistoryItemDto(
        String id,
        String planName,
        String completedAt,
        Long completedKm,
        String workOrderId,
        String notes
) {
}
