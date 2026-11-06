package org.fleetguard.event;

import org.fleetguard.entity.TripKmAlertReason;

import java.util.UUID;

/** CAM-152: un viaje se cerró con un kilometraje fuera de lo normal. tripKm y averageKm pueden faltar. */
public record TripKmAnomalyEvent(UUID vehicleId, String plate, String driverName, TripKmAlertReason reason,
                                 Long tripKm, Long averageKm, long declaredKm, long vehicleKm) {
}
