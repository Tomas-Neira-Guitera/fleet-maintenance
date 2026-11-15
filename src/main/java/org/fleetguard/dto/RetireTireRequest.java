package org.fleetguard.dto;

/** Body de POST /api/tires/{id}/retire (CAM-157): dar de baja una cubierta del stock. */
public record RetireTireRequest(String reason) {
}
