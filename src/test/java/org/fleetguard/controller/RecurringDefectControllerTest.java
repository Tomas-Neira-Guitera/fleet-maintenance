package org.fleetguard.controller;

import org.fleetguard.dto.RecurringDefectsDto;
import org.fleetguard.entity.Role;
import org.fleetguard.service.RecurringDefectService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/defects/recurring (CAM-135): solo lectura y solo para el admin. */
@WebMvcTest(RecurringDefectController.class)
class RecurringDefectControllerTest extends WebMvcTestBase {

    @MockBean
    private RecurringDefectService service;

    @Test
    void elAdminVeLosControlesRecurrentesDeUnVehiculo() throws Exception {
        Instant at = Instant.parse("2026-10-29T15:00:00Z");
        when(service.list("v-1")).thenReturn(new RecurringDefectsDto(3, 30, List.of(
                new RecurringDefectsDto.Item("v-1", "AB123CD", "frenos", "Frenos", 3, at, List.of(
                        new RecurringDefectsDto.Occurrence("d-1", at, "blocking", "open", "Pastillas gastadas"))))));

        mvc.perform(get("/defects/recurring").param("vehicleId", "v-1")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threshold").value(3))
                .andExpect(jsonPath("$.windowDays").value(30))
                .andExpect(jsonPath("$.items[0].plate").value("AB123CD"))
                .andExpect(jsonPath("$.items[0].itemLabel").value("Frenos"))
                .andExpect(jsonPath("$.items[0].count").value(3))
                .andExpect(jsonPath("$.items[0].lastReportedAt").value("2026-10-29T15:00:00Z"))
                .andExpect(jsonPath("$.items[0].occurrences[0].severity").value("blocking"));
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(get("/defects/recurring"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/defects/recurring").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/defects/recurring").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
