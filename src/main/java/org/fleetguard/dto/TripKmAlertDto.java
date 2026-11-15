package org.fleetguard.dto;

/**
 * CAM-152: la marca de km fuera de lo normal de un viaje, en su post-viaje del historial. reason es
 * above_average, below_average o below_vehicle_km. tripKm y averageKm pueden faltar con
 * below_vehicle_km (sin pre-viaje con km, o sin viajes suficientes para un promedio).
 */
public record TripKmAlertDto(String reason, Long tripKm, Long averageKm, Long declaredKm, Long vehicleKm) {
}
