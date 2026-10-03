package org.example.dto;

/** Body de POST /api/users (CAM-23). role: ADMIN | CHOFER | TECNICO. */
public record CreateUserRequest(
        String username,
        String password,
        String role
) {
}
