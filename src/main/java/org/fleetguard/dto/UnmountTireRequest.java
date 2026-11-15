package org.fleetguard.dto;

/** Body de POST /api/vehicles/{id}/tires/unmount (CAM-157): retire=true la da de baja, si no vuelve al stock. */
public record UnmountTireRequest(String position, Boolean retire, String reason) {
}
