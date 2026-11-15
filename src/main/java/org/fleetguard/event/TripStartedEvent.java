package org.fleetguard.event;

import java.time.Instant;
import java.util.UUID;

/** CAM-143: un chofer salió de viaje (pre-viaje sin defecto bloqueante). startedAt es la hora del pre-viaje. */
public record TripStartedEvent(UUID vehicleId, String plate, String driverName, Instant startedAt) {
}
