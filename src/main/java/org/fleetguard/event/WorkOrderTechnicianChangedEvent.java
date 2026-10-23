package org.fleetguard.event;

import java.util.UUID;

/**
 * Una OT abierta cambió de técnico a cargo (CAM-99, CAM-100): se creó con técnico, se le asignó uno,
 * se reasignó a otro o se desasignó. Cualquiera de los dos puede ser null, nunca los dos.
 */
public record WorkOrderTechnicianChangedEvent(UUID workOrderId, UUID previousTechnicianId, UUID newTechnicianId) {
}
