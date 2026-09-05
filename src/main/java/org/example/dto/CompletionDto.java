package org.example.dto;

/** Coincide con un item de GET .../completions. */
public record CompletionDto(
        String id,
        String completedAt,
        Long completedKm,
        String workOrderId,
        String notes
) {
}
