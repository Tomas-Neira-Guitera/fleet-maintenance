package org.fleetguard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/vehicles/{id}/fuel (CAM-156): consumo de los últimos meses. Los litros y montos son los de las
 * cargas del período; el consumo y el costo por km, los de los tramos que cierran en el período.
 */
public record VehicleFuelDto(String vehicleId, String plate, int months, Summary summary, List<Month> monthly,
                             List<FuelLoadDto> loads) {

    public record Summary(int loadCount, BigDecimal liters, BigDecimal amount, long measuredKm,
                          BigDecimal litersPer100Km, BigDecimal costPerKm) {
    }

    /** month: "2026-06". */
    public record Month(String month, BigDecimal liters, BigDecimal amount, long measuredKm,
                        BigDecimal litersPer100Km, BigDecimal costPerKm) {
    }
}
