package org.fleetguard.controller;

import org.fleetguard.dto.DowntimeDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.service.DowntimeService;
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

/** GET /api/downtime (CAM-134): solo lectura y solo para el admin. */
@WebMvcTest(DowntimeController.class)
class DowntimeControllerTest extends WebMvcTestBase {

    @MockBean
    private DowntimeService service;

    @Test
    void elAdminVeLaDisponibilidadDeLaFlota() throws Exception {
        DowntimeDto.RepairTimes repairs = new DowntimeDto.RepairTimes(8.0, 2,
                new DowntimeDto.Average(8.0, 2), new DowntimeDto.Average(null, 0));
        when(service.get("2026-10-05", "2026-10-11", null)).thenReturn(new DowntimeDto(
                Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-12T00:00:00Z"),
                new DowntimeDto.Fleet(85.7, 48.0, 1, repairs),
                List.of(new DowntimeDto.Vehicle("v-1", "AB123CD", 71.4, 48.0, 1, repairs))));

        mvc.perform(get("/downtime").param("from", "2026-10-05").param("to", "2026-10-11")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleet.availabilityPct").value(85.7))
                .andExpect(jsonPath("$.fleet.repairs.internal.avgHours").value(8.0))
                .andExpect(jsonPath("$.fleet.repairs.external.avgHours").isEmpty())
                .andExpect(jsonPath("$.vehicles[0].plate").value("AB123CD"))
                .andExpect(jsonPath("$.vehicles[0].stops").value(1));
    }

    @Test
    void unPeriodoInvalidoDevuelve422() throws Exception {
        when(service.get(null, null, null)).thenThrow(new WorkOrderValidationException("Período inválido",
                List.of(new FieldValidationErrorDetail("from", "Obligatorio"))));

        mvc.perform(get("/downtime").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("from"));
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(get("/downtime"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/downtime").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/downtime").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
