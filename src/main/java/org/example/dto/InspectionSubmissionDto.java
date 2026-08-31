package org.example.dto;

import java.util.List;

/** Coincide con components.schemas.InspectionSubmission de openapi.yaml -- body de POST /api/inspections/{vehicleId}. */
public record InspectionSubmissionDto(String type, List<ChecklistAnswerDto> answers, String notes) {
}
