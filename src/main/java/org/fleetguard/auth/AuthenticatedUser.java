package org.fleetguard.auth;

import org.fleetguard.entity.Role;

import java.util.UUID;

/**
 * Usuario dueño del JWT de un pedido ya validado por JwtAuthInterceptor (CAM-23). El rol sale
 * de la base, no del token: si a alguien le cambian el rol, el cambio rige desde el pedido siguiente.
 */
public record AuthenticatedUser(UUID id, String username, Role role) {

    /** Atributo del request donde lo deja el interceptor (para @RequestAttribute en los controllers). */
    public static final String REQUEST_ATTRIBUTE = "fleetguard.authenticatedUser";
}
