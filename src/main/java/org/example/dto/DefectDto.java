package org.example.dto;

/** Coincide con components.schemas.DefectSummary de openapi.yaml. */
public record DefectDto(
        String id,
        String severity,
        String description,
        String photoUrl,
        String createdAt,
        String vehiclePlate,
        String status,
        String reportedBy
) {
}
