package org.fleetguard.controller;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.PushConfigDto;
import org.fleetguard.dto.PushSubscriptionRequest;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.PushValidationException;
import org.fleetguard.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/push (CAM-119): cualquier rol logueado maneja sus propias suscripciones. */
@WebMvcTest(PushController.class)
class PushControllerTest extends WebMvcTestBase {

    private static final String SUBSCRIPTION = """
            {"endpoint": "https://fcm.googleapis.com/fcm/send/abc", "expirationTime": null,
             "keys": {"p256dh": "BPk...", "auth": "xyz"}}""";

    @MockBean
    private PushNotificationService service;

    @Test
    void cualquierRolPideLaClavePublica() throws Exception {
        when(service.config()).thenReturn(new PushConfigDto("BKxl"));

        for (Role role : Role.values()) {
            mvc.perform(get("/push/public-key").header(HttpHeaders.AUTHORIZATION, loginAs("u-" + role, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicKey").value("BKxl"));
        }
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/push/public-key")).andExpect(status().isUnauthorized());
        mvc.perform(post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUBSCRIPTION))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void suscribirseGuardaLaSuscripcionDelUsuarioDelToken() throws Exception {
        mvc.perform(post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUBSCRIPTION)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isNoContent());

        verify(service).subscribe(any(UUID.class), eq(new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/abc", new PushSubscriptionRequest.Keys("BPk...", "xyz"))));
    }

    @Test
    void unaSuscripcionInvalidaDevuelve422ConElCampo() throws Exception {
        doThrow(new PushValidationException("Suscripción inválida",
                List.of(new FieldValidationErrorDetail("keys.auth", "Obligatoria"))))
                .when(service).subscribe(any(), any());

        mvc.perform(post("/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer", Role.CHOFER)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("keys.auth"));
    }

    @Test
    void darseDeBajaPorEndpoint() throws Exception {
        mvc.perform(delete("/push/subscriptions").param("endpoint", "https://fcm.googleapis.com/fcm/send/abc")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isNoContent());

        verify(service).unsubscribe(any(UUID.class), eq("https://fcm.googleapis.com/fcm/send/abc"));
    }

    @Test
    void laPruebaDiceACuantosNavegadoresLlego() throws Exception {
        when(service.sendTest(any())).thenReturn(2);

        mvc.perform(post("/push/test").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(2));
    }
}
