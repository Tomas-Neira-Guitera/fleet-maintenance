package org.fleetguard.dto;

/**
 * Coincide con components.schemas.InspectionHistoryItem de openapi.yaml. kmAlert (CAM-152) solo
 * viene en el post-viaje de un viaje marcado con km fuera de lo normal; si no, null.
 */
public record InspectionHistoryItemDto(
        String id,
        String type,
        String timestamp,
        String driverName,
        Double odometerKm,
        String notes,
        boolean hasBlockingDefect,
        TripKmAlertDto kmAlert
) {
}
