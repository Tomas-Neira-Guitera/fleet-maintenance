package org.fleetguard.dto;

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
        String details,
        // CAM-139: dónde se reportó: "pre-trip", "post-trip" u "on-road" (durante el viaje).
        String source,
        // CAM-146: null si no se puede decir que ocurrió en el viaje.
        OccurredOnTripDto occurredOnTrip,
        // CAM-142: días desde que se reportó, si está sin atender (abierto, pasado el plazo, sin OT abierta ni
        // turno programado); null si no. Solo lo calcula GET /api/defects.
        Long unattendedDays
) {
    public DefectDto(String id, String severity, String description, String photoUrl, String createdAt,
                     String vehicleId, String vehiclePlate, String status, String reportedBy, String details,
                     String source, OccurredOnTripDto occurredOnTrip) {
        this(id, severity, description, photoUrl, createdAt, vehicleId, vehiclePlate, status, reportedBy, details,
                source, occurredOnTrip, null);
    }

    public DefectDto(String id, String severity, String description, String photoUrl, String createdAt,
                     String vehicleId, String vehiclePlate, String status, String reportedBy, String details,
                     String source) {
        this(id, severity, description, photoUrl, createdAt, vehicleId, vehiclePlate, status, reportedBy, details,
                source, null);
    }

    public DefectDto withUnattendedDays(Long days) {
        return new DefectDto(id, severity, description, photoUrl, createdAt, vehicleId, vehiclePlate, status, reportedBy,
                details, source, occurredOnTrip, days);
    }

    public DefectDto(String id, String severity, String description, String photoUrl, String createdAt,
                     String vehicleId, String vehiclePlate, String status, String reportedBy, String details) {
        this(id, severity, description, photoUrl, createdAt, vehicleId, vehiclePlate, status, reportedBy, details,
                null, null);
    }

    public DefectDto(String id, String severity, String description, String photoUrl, String createdAt,
                     String vehicleId, String vehiclePlate, String status, String reportedBy) {
        this(id, severity, description, photoUrl, createdAt, vehicleId, vehiclePlate, status, reportedBy, null,
                null, null);
    }
}
