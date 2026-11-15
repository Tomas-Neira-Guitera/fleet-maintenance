package org.fleetguard.event;

import java.math.BigDecimal;
import java.util.UUID;

/** CAM-156: una carga cerró un tramo con un consumo muy por encima del promedio del vehículo. */
public record FuelConsumptionAnomalyEvent(UUID vehicleId, String plate, BigDecimal litersPer100Km,
                                          BigDecimal averageLitersPer100Km, long km) {
}
