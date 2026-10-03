package org.fleetguard.dto;

/** 201 Response de POST .../completions. */
public record CompletionResultDto(
        String id,
        String assignmentId,
        String completedAt,
        Long completedKm,
        String workOrderId,
        String notes,
        AssignmentSummaryDto updatedAssignment
) {
}
