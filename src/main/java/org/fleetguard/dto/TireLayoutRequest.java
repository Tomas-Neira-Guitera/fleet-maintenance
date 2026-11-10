package org.fleetguard.dto;

/** Body de PUT /api/vehicles/{id}/tire-layout (CAM-157): 4_ruedas | 6_ruedas | 10_ruedas. */
public record TireLayoutRequest(String layout) {
}
