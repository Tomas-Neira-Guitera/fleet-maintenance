package org.fleetguard.controller;

import org.fleetguard.dto.MaintenanceComplianceDto;
import org.fleetguard.entity.Role;
import org.fleetguard.service.MaintenanceComplianceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/maintenance-compliance (CAM-155): solo lectura y solo para el admin. */
@WebMvcTest(MaintenanceComplianceController.class)
class MaintenanceComplianceControllerTest extends WebMvcTestBase {

    @MockBean
    private MaintenanceComplianceService service;

    @Test
    void elAdminVeLosIndicadores() throws Exception {
        when(service.get("2026-10-01", "2026-10-31")).thenReturn(new MaintenanceComplianceDto(
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"),
                new MaintenanceComplianceDto.Preventive(9, 3, 75.0),
                new MaintenanceComplianceDto.DefectResolution(4, null),
                new MaintenanceComplianceDto.OpenWorkOrders(5, 2, 2, 1)));

        mvc.perform(get("/maintenance-compliance").param("from", "2026-10-01").param("to", "2026-10-31")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preventive.onTimePct").value(75.0))
                .andExpect(jsonPath("$.defects.avgHours").isEmpty())
                .andExpect(jsonPath("$.openWorkOrders.over30Days").value(1));
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(get("/maintenance-compliance"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/maintenance-compliance").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
