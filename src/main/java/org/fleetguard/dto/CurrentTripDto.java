package org.fleetguard.dto;

/** Viaje abierto de un vehículo: desde cuándo y quién lo abrió (null si no se sabe) -- CAM-87. */
public record CurrentTripDto(String startedAt, UserRefDto openedBy) {
}
