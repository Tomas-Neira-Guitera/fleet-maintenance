package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Una cubierta (CAM-157). km: lo que rodó en todos sus montajes (el auxilio no suma). costPerKm: null
 * sin km. current: dónde está montada, null si no. condition: el último problema reportado que sigue
 * abierto; null si no tiene.
 */
public record TireDto(String id, String code, String brand, String model, String size, String purchaseDate,
                      BigDecimal cost, String status, String retiredAt, String retiredReason,
                      long km, BigDecimal costPerKm, Current current, TireConditionReportDto condition) {

    public record Current(String vehicleId, String plate, String position, String positionLabel, String mountedAt,
                          long mountedKm) {
    }

}
