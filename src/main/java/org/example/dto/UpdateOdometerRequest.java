package org.example.dto;

/** Body de PATCH /api/vehicles/{id}/odometer -- feature 5.5. */
public record UpdateOdometerRequest(long odometerKm) {
}
