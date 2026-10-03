package org.fleetguard.dto;

public record OdometerResultDto(String vehicleId, long odometerKm, String updatedAt) {
}
