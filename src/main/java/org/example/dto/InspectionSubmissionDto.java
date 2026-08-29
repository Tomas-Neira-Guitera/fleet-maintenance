package org.example.dto;

import java.util.List;

/** Matches components.schemas.InspectionSubmission in openapi.yaml -- the POST /api/inspections/{vehicleId} request body. */
public record InspectionSubmissionDto(String type, List<ChecklistAnswerDto> answers, String notes) {
}
