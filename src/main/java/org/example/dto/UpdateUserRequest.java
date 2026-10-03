package org.example.dto;

/**
 * Body de PATCH /api/users/{id} (CAM-23) -- campos parciales: cambiar el rol, resetear la
 * contraseña (password) y activar o desactivar (active). El username no se edita.
 */
public record UpdateUserRequest(
        String role,
        String password,
        Boolean active
) {
}
