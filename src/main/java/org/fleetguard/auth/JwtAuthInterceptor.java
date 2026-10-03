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
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;

/**
 * Exige un JWT válido de un usuario ADMIN activo (CAM-23). Hoy se registra solo para /users/**
 * (ver WebConfig); CAM-73 lo extiende al resto de la API.
 *
 * Es un interceptor de Spring MVC y no un filtro de servlet a propósito: corre después del
 * manejo de CORS (las respuestas 401/403 salen con sus headers y el navegador las puede leer)
 * y sus excepciones pasan por GlobalExceptionHandler, con el mismo formato de error que el resto.
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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

        // Se consulta la base en cada pedido: un usuario desactivado (o al que le sacaron el rol)
        // pierde el acceso ya, sin esperar a que venza su token.
        Optional<User> user = userRepository.findById(userId).filter(User::isActive);
        if (user.isEmpty()) {
            throw new UnauthorizedException();
        }
        if (user.get().getRole() != Role.ADMIN) {
            throw new ForbiddenException();
        }

        request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE,
                new AuthenticatedUser(user.get().getId(), user.get().getUsername(), user.get().getRole()));
        return true;
    }
}
