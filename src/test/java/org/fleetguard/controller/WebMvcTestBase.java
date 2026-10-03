package org.fleetguard.controller;

import org.fleetguard.repository.UserRepository;
import org.fleetguard.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de los tests de controller (@WebMvcTest). Cada test levanta solo la capa web real
 * (controller, Jackson, GlobalExceptionHandler, CORS) con el service mockeado, para comprobar
 * lo que promete openapi.yaml: ruta, código HTTP, forma del JSON y formato de error.
 *
 * JwtAuthInterceptor (CAM-23) se registra en toda la capa web y necesita estos dos beans; acá
 * van mockeados porque solo /api/users lo usa, y eso lo prueba UserControllerAuthTest.
 */
abstract class WebMvcTestBase {

    @Autowired
    protected MockMvc mvc;

    @MockBean
    protected JwtService jwtService;

    @MockBean
    protected UserRepository userRepository;
}
