package org.example.dto;

/** Coincide con components.schemas.DefectSummary de openapi.yaml. */
public record DefectDto(
        String id,
        String severity,
        String description,
        String photoUrl,
        String createdAt,
        String vehicleId,
        String vehiclePlate,
        String status,
        String reportedBy,
        // CAM-32: descripción larga del defecto (opcional). description es el título corto.
        String details
) {
    public DefectDto(String id, String severity, String description, String photoUrl, String createdAt,
                     String vehicleId, String vehiclePlate, String status, String reportedBy) {
        this(id, severity, description, photoUrl, createdAt, vehicleId, vehiclePlate, status, reportedBy, null);
    }
}
