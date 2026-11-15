package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Una carga de combustible (CAM-156). consumption: el tramo que cierra esta carga (de tanque lleno a
 * tanque lleno), null si no cierra ninguno. alert: si al cargarla se avisó de un consumo anormal.
 */
public record FuelLoadDto(String id, String vehicleId, String plate, String loadedAt, BigDecimal liters,
                          BigDecimal amount, long odometerKm, boolean fullTank, UserRefDto createdBy,
                          Consumption consumption, Alert alert) {

    public record Consumption(long km, BigDecimal liters, BigDecimal litersPer100Km, BigDecimal costPerKm) {
    }

    public record Alert(BigDecimal litersPer100Km, BigDecimal averageLitersPer100Km) {
    }
}
