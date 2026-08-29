package org.example.dto;

import java.util.List;

/** Matches components.schemas.Inspection in openapi.yaml. */
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
