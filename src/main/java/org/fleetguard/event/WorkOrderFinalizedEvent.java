package org.fleetguard.event;

import java.util.UUID;

/** CAM-98: una OT pasó a finalizada. */
public record WorkOrderFinalizedEvent(UUID workOrderId) {
}
