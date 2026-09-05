package org.example.dto;

/** Body de POST .../completions. */
public record CreateCompletionRequest(
        String completedAt,
        Long completedKm,
        String workOrderId,
        String notes
) {
}
