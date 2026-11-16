package org.fleetguard.dto;

/** Un montaje de un neumático (CAM-157). removedAt y removedKm null mientras sigue puesto. */
public record TireMountingDto(String tireId, String tireLabel, String vehicleId, String plate, String position,
                              String positionLabel, String mountedAt, long mountedKm, String removedAt, Long removedKm,
                              long km) {
}
