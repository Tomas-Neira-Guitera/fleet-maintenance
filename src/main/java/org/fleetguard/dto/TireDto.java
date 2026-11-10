package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Una cubierta (CAM-157). km: lo que rodó en todos sus montajes (el auxilio no suma). costPerKm: null
 * sin km. current: dónde está montada, null si no. lastTread: la última medición del dibujo.
 */
public record TireDto(String id, String code, String brand, String model, String size, String purchaseDate,
                      BigDecimal cost, BigDecimal initialTreadMm, String status, String retiredAt, String retiredReason,
                      long km, BigDecimal costPerKm, Current current, Tread lastTread) {

    public record Current(String vehicleId, String plate, String position, String positionLabel, String mountedAt,
                          long mountedKm) {
    }

    public record Tread(BigDecimal depthMm, String measuredAt, String measuredBy, boolean belowMinimum) {
    }
}
