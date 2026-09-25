package org.example.dto;

/** Coincide con components.schemas.InspectionHistoryItem de openapi.yaml. */
public record InspectionHistoryItemDto(
        String id,
        String type,
        String timestamp,
        String driverName,
        Double odometerKm,
        String notes,
        boolean hasBlockingDefect
) {
}
