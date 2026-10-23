package org.fleetguard.dto;

/**
 * Body de PATCH /api/users/{id} -- campos parciales: renombrar (username), resetear la contraseña
 * (password) y activar o desactivar (active). El rol no se edita (CAM-91): pedir uno distinto es un 422.
 */
public record UpdateUserRequest(
        String username,
        String password,
        Boolean active,
        String role,
        // CAM-117: null no lo toca, "" lo borra.
        String email
) {
}
