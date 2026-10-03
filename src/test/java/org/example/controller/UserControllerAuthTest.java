package org.example.controller;

import org.example.entity.Role;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.example.service.JwtService;
import org.example.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CAM-23: /api/users exige un JWT de un ADMIN activo. Se prueba con el JwtService real (tokens
 * firmados de verdad) y el interceptor real; solo la base y el service están mockeados.
 */
@WebMvcTest(UserController.class)
@Import(JwtService.class)
class UserControllerAuthTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserService userService;

    private User userInDb(String username, Role role, boolean active) throws Exception {
        User user = new User(username, "hash", role);
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, UUID.randomUUID());
        user.setActive(active);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verifyNoInteractions(userService);
    }

    @Test
    void tokenAdulteradoDevuelve401() throws Exception {
        User admin = userInDb("admin", Role.ADMIN, true);
        String token = jwtService.generateToken(admin);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoDevuelve401() throws Exception {
        User admin = userInDb("admin", Role.ADMIN, true);
        JwtService alreadyExpired = new JwtService(
                "HUKfjtGyMHxMPOgQMtZsS5XQD8pMYMAxNTa2ss1fa68R8B29qcfAnxSB7SYQr2JG", -1);

        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + alreadyExpired.generateToken(admin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeChoferDevuelve403() throws Exception {
        User chofer = userInDb("chofer", Role.CHOFER, true);

        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, bearer(chofer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    void tokenDeTecnicoDevuelve403TambienEnElAlta() throws Exception {
        User tecnico = userInDb("tecnico", Role.TECNICO, true);

        mvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, bearer(tecnico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    void adminDesactivadoDevuelve401AunqueSuTokenSigaVigente() throws Exception {
        User admin = userInDb("admin", Role.ADMIN, false);

        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAccedeAlListado() throws Exception {
        User admin = userInDb("admin", Role.ADMIN, true);
        when(userService.list(null)).thenReturn(List.of());

        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void elPatchRecibeAlAdminQueHaceElPedido() throws Exception {
        User admin = userInDb("admin", Role.ADMIN, true);
        String targetId = UUID.randomUUID().toString();

        mvc.perform(patch("/users/" + targetId).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        verify(userService).update(eq(targetId), any(), eq(admin.getId()));
    }

    @Test
    void elPreflightDeCorsPasaSinToken() throws Exception {
        mvc.perform(options("/users")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void el401SaleConHeadersDeCorsParaQueElFrontendLoPuedaLeer() throws Exception {
        mvc.perform(get("/users").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }
}
