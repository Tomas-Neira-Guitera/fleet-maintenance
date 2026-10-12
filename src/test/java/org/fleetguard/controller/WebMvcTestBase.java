package org.fleetguard.controller;

import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Base de los tests de controller (@WebMvcTest). Cada test levanta solo la capa web real
 * (controller, Jackson, GlobalExceptionHandler, CORS) con el service mockeado, para comprobar
 * lo que promete openapi.yaml: ruta, código HTTP, forma del JSON y formato de error.
 *
 * JwtAuthInterceptor (CAM-23) se registra en toda la capa web y necesita estos dos beans; acá
 * van mockeados. El interceptor real (firma, vencimiento, usuario desactivado) lo prueba
 * UserControllerAuthTest; las rutas protegidas de otros controllers usan loginAs.
 */
abstract class WebMvcTestBase {

    @Autowired
    protected MockMvc mvc;

    @MockBean
    protected JwtService jwtService;

    @MockBean
    protected UserRepository userRepository;

    /** Deja logueado a un usuario activo con ese rol y devuelve el header Authorization a mandar. */
    protected String loginAs(String username, Role role) throws Exception {
        User user = new User(username, "hash", role);
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, UUID.randomUUID());
        String token = "token-" + username;
        when(jwtService.parseUserId(token)).thenReturn(Optional.of(user.getId()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return "Bearer " + token;
    }
}
