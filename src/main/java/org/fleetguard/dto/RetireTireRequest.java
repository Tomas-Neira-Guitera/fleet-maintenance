package org.fleetguard.dto;

/** Body de POST /api/tires/{id}/retire (CAM-157): dar de baja un neumático del stock. */
public record RetireTireRequest(String reason) {
}
