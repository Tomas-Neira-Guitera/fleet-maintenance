package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Body de POST y PUT /api/fuel-loads (CAM-156). loadedAt opcional (ahora); fullTank opcional (true).
 * vehicleId solo se usa en el alta: una carga no se pasa a otro vehículo.
 */
public record FuelLoadRequest(String vehicleId, String loadedAt, BigDecimal liters, BigDecimal amount,
                              Long odometerKm, Boolean fullTank) {
}
