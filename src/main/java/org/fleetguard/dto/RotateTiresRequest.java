package org.fleetguard.dto;

/** Body de POST /api/vehicles/{id}/tires/rotate (CAM-157): de from a to; si to tiene cubierta, se intercambian. */
public record RotateTiresRequest(String from, String to) {
}
