package org.fleetguard.dto;

/**
 * Body de POST /api/vehicles/{id}/tires/mount (CAM-157). Si la posición ya tiene una cubierta, replaced dice
 * qué pasa con ella: "stock" o "retired" (con retiredReason opcional). Sin replaced, la posición ocupada da 409.
 */
public record MountTireRequest(String tireId, String position, String replaced, String retiredReason) {
}
