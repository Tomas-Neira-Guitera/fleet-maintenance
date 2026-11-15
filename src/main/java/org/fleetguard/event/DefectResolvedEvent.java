package org.fleetguard.event;

import java.util.UUID;

/**
 * CAM-141: se finalizó la OT de un defecto que seguía abierto. vehicleBecameAvailable: si con eso el vehículo
 * volvió a estar disponible; en ese caso quien lo reportó ya recibe VEHICLE_AVAILABLE (CAM-103) y no se le
 * manda este otro.
 */
public record DefectResolvedEvent(UUID defectId, UUID workOrderId, boolean vehicleBecameAvailable) {
}
