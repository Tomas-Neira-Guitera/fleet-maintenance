package org.fleetguard.dto;

import java.util.List;

/** GET /api/vehicles/{id}/tire-positions (CAM-157): para dibujar el vehículo en el pre-viaje del chofer. */
public record TirePositionsDto(String vehicleType, List<TirePositionDto> positions) {
}
