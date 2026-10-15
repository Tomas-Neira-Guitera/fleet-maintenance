package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * GET /api/vehicles/{id}/costs (CAM-105): cuánto costó mantener el vehículo.
 * firstKnownKm, kmDriven y costPerKm vienen en null cuando no hay km suficientes para calcularlos.
 */
public record VehicleCostsDto(
        BigDecimal totalAmount,
        BigDecimal last12MonthsAmount,
        int expenseCount,
        long currentKm,
        Long firstKnownKm,
        Long kmDriven,
        BigDecimal costPerKm
) {
}
