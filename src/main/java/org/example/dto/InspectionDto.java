package org.example.dto;

import java.util.List;

/** Coincide con components.schemas.Inspection de openapi.yaml. */
public record InspectionDto(
        String id,
        String tripId,
        String vehicleId,
        String driverId,
        String type,
        String timestamp,
        Double odometerKm,
        List<ChecklistAnswerDto> answers,
        String notes,
        boolean hasBlockingDefect
) {
}
