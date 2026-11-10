package org.fleetguard.event;

import java.math.BigDecimal;
import java.util.UUID;

/** CAM-157: se midió una cubierta con el dibujo por debajo del mínimo. */
public record TireTreadLowEvent(UUID vehicleId, String plate, String tireLabel, String positionLabel,
                                BigDecimal depthMm, BigDecimal minMm, String measuredBy) {
}
