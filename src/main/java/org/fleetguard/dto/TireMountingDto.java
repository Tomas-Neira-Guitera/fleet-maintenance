package org.fleetguard.dto;

/** Un montaje de una cubierta (CAM-157). removedAt y removedKm null mientras sigue puesta. */
public record TireMountingDto(String tireId, String tireLabel, String vehicleId, String plate, String position,
                              String positionLabel, String mountedAt, long mountedKm, String removedAt, Long removedKm,
                              long km) {
}
