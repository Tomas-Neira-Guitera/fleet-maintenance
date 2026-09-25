package org.example.dto;

/** Coincide con GET /api/users -- nunca expone el hash de la contraseña. */
public record UserSummaryDto(
        String id,
        String username,
        String role
) {
}
