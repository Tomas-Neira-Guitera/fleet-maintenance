package org.fleetguard.dto;

/** Coincide con components.schemas.Trip de openapi.yaml. openedBy/closedBy: null en viajes sin chofer cargado (CAM-87). */
public record TripDto(
        String id,
        String vehicleId,
        String status,
        String startedAt,
        String endedAt,
        UserRefDto openedBy,
        UserRefDto closedBy
) {
}
