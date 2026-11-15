package org.fleetguard.dto;

/** Body de PUT /api/vehicles/{trailerId}/coupling: el tractor o camión que lo arrastra. */
public record CoupleRequest(String towingId) {
}
