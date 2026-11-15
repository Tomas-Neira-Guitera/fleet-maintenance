package org.fleetguard.event;

import java.util.List;
import java.util.UUID;

/** CAM-135: una inspección se guardó con defectos, sean bloqueantes o no. itemIds: los controles que fallaron. */
public record DefectsReportedEvent(UUID vehicleId, String plate, List<String> itemIds) {
}
