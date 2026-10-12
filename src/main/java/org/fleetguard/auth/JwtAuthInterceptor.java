package org.fleetguard.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.exception.ForbiddenException;
import org.fleetguard.exception.UnauthorizedException;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.service.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Exige un JWT válido de un usuario activo con alguno de los roles permitidos (CAM-23, CAM-85).
 * Se registra por ruta en WebConfig, cada una con su rol; CAM-73 lo extiende al resto de la API.
 *
 * Es un interceptor de Spring MVC y no un filtro de servlet a propósito: corre después del
 * manejo de CORS (las respuestas 401/403 salen con sus headers y el navegador las puede leer)
 * y sus excepciones pasan por GlobalExceptionHandler, con el mismo formato de error que el resto.
 */
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final Set<Role> allowedRoles;

    public JwtAuthInterceptor(JwtService jwtService, UserRepository userRepository, Role... allowedRoles) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.allowedRoles = Set.of(allowedRoles);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // El preflight de CORS nunca trae el token.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException();
        }
        UUID userId = jwtService.parseUserId(header.substring(BEARER_PREFIX.length()).trim())
                .orElseThrow(UnauthorizedException::new);

        // Se consulta la base en cada pedido: un usuario desactivado pierde el acceso ya, sin
        // esperar a que venza su token.
        Optional<User> user = userRepository.findById(userId).filter(User::isActive);
        if (user.isEmpty()) {
            throw new UnauthorizedException();
        }
        if (!allowedRoles.contains(user.get().getRole())) {
            throw new ForbiddenException();
        }

        request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE,
                new AuthenticatedUser(user.get().getId(), user.get().getUsername(), user.get().getRole()));
        return true;
    }
}
