package org.example.dto;

/** Matches components.schemas.Trip in openapi.yaml. */
public record TripDto(
        String id,
        String vehicleId,
        String status,
        String startedAt,
        String endedAt
) {
}
