package org.fleetguard.controller;

import org.fleetguard.dto.NotificationDto;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.NotificationNotFoundException;
import org.fleetguard.service.NotificationInboxService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/notifications (CAM-96): cualquier rol, siempre con sus propios avisos. */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends WebMvcTestBase {

    @MockBean
    private NotificationInboxService service;

    @Test
    void cualquierRolListaSusAvisos() throws Exception {
        when(service.list(any(), isNull())).thenReturn(List.of(new NotificationDto("1", "work_order_finalized", "T", "M",
                "/ordenes-trabajo/9", "2026-10-23T12:00:00Z", null)));

        for (Role role : Role.values()) {
            mvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, loginAs("u-" + role, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].type").value("work_order_finalized"))
                    .andExpect(jsonPath("$.items[0].link").value("/ordenes-trabajo/9"))
                    .andExpect(jsonPath("$.items[0].readAt").doesNotExist());
        }
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/notifications/1/dismiss")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void elContadorDeSinLeer() throws Exception {
        when(service.unreadCount(any())).thenReturn(3L);

        mvc.perform(get("/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void marcarLeidoMarcarTodosYDescartarDevuelven204() throws Exception {
        String auth = loginAs("tecnico", Role.TECNICO);
        mvc.perform(patch("/notifications/abc/read").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNoContent());
        mvc.perform(patch("/notifications/read-all").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNoContent());
        mvc.perform(patch("/notifications/abc/dismiss").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNoContent());

        verify(service).markRead(any(UUID.class), eq("abc"));
        verify(service).markAllRead(any(UUID.class));
        verify(service).dismiss(any(UUID.class), eq("abc"));
    }

    @Test
    void unAvisoAjenoEs404() throws Exception {
        doThrow(new NotificationNotFoundException("NOTIFICATION_NOT_FOUND", "No existe un aviso con id x"))
                .when(service).dismiss(any(), eq("x"));

        mvc.perform(patch("/notifications/x/dismiss").header(HttpHeaders.AUTHORIZATION, loginAs("chofer", Role.CHOFER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOTIFICATION_NOT_FOUND"));
    }
}
