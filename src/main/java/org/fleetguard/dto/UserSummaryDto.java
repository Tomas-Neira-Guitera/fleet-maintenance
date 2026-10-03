package org.fleetguard.dto;

/** Coincide con components.schemas.UserSummary (GET/POST/PATCH /api/users) -- nunca expone el hash de la contraseña. */
public record UserSummaryDto(
        String id,
        String username,
        String role,
        // CAM-23: false = desactivado (no puede loguearse).
        boolean active
) {
}
