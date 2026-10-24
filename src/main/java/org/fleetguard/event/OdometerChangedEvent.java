package org.fleetguard.event;

import java.util.UUID;

/** CAM-118: subió el km de un vehículo, así que algún plan por km puede haber entrado en su aviso. */
public record OdometerChangedEvent(UUID vehicleId) {
}
