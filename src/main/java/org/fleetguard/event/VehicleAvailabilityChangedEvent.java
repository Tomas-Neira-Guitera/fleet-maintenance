package org.fleetguard.event;

import org.fleetguard.dto.BlockReasonDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El vehículo pasó de disponible a "No disponible" o al revés (CAM-102/103). reasons: los motivos al
 * bloquearse (vacío al liberarse). unavailableSince: desde cuándo estaba bloqueado, para saber a quién
 * avisarle cuando vuelve. workOrderId: la OT que lo liberó, si fue así.
 */
public record VehicleAvailabilityChangedEvent(UUID vehicleId, String plate, boolean available,
                                              List<BlockReasonDto> reasons, Instant unavailableSince,
                                              UUID workOrderId) {
}
