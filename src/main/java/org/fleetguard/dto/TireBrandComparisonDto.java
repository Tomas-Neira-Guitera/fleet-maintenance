package org.fleetguard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/tires/brand-comparison (CAM-157): costo por km por marca, de las cubiertas con al menos minKm
 * km. costPerKm = costo total / km totales. Ordenado del más barato por km al más caro.
 */
public record TireBrandComparisonDto(long minKm, List<Brand> brands) {

    public record Brand(String brand, int tires, int retired, long totalKm, BigDecimal totalCost,
                        BigDecimal costPerKm, long averageKm) {
    }
}
