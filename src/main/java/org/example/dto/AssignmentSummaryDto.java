package org.example.dto;

/** Subconjunto de AssignmentDto devuelto inline en CompletionResultDto para refrescar el chip de estado. */
public record AssignmentSummaryDto(
        String id,
        Long nextDueKm,
        String nextDueDate,
        String status
) {
}
