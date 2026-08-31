package org.example.dto;

/** Coincide con components.schemas.Trip de openapi.yaml. */
public record TripDto(
        String id,
        String vehicleId,
        String status,
        String startedAt,
        String endedAt
) {
}
