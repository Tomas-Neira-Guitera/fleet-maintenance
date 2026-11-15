package org.fleetguard.event;

import java.util.UUID;

/** CAM-127: se cargó, renovó, corrigió o borró un documento. Después del commit se revisan sus avisos. */
public record VehicleDocumentChangedEvent(UUID vehicleId) {
}
