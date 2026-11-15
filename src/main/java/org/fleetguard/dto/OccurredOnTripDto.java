package org.fleetguard.dto;

/**
 * CAM-146: el defecto del post-viaje apareció durante ese viaje. Coincide con
 * components.schemas.OccurredOnTrip de openapi.yaml.
 */
public record OccurredOnTripDto(
        String tripId,
        String tripStartedAt,
        String tripEndedAt,
        // El pre-viaje en que ese control dio OK; null si el viaje no tiene pre-viaje guardado.
        String preTripInspectionId,
        String preTripAt
) {
}
