package org.fleetguard.event;

import java.util.UUID;

/** Se canceló una OT que tenía técnico a cargo (CAM-100). technicianId es el que la tenía. */
public record WorkOrderCancelledEvent(UUID workOrderId, UUID technicianId) {
}
