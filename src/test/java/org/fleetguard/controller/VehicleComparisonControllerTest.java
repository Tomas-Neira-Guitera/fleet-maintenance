package org.fleetguard.controller;

import org.fleetguard.dto.VehicleComparisonDto;
import org.fleetguard.entity.Role;
import org.fleetguard.service.VehicleComparisonService;
import org.fleetguard.service.VehicleHistoryService;
import org.fleetguard.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/vehicles/comparison (CAM-140): solo lectura y solo para el admin. Se carga también
 * VehicleController, para que "comparison" no se tome como el id de GET /api/vehicles/{id}.
 */
@WebMvcTest({VehicleComparisonController.class, VehicleController.class})
class VehicleComparisonControllerTest extends WebMvcTestBase {

    @MockBean
    private VehicleComparisonService service;

    @MockBean
    private VehicleService vehicleService;

    @MockBean
    private VehicleHistoryService vehicleHistoryService;

    @Test
    void elAdminVeLaComparacionConLosNullsDeLosQueNoTienenDato() throws Exception {
        VehicleComparisonDto.Row row = new VehicleComparisonDto.Row("v-1", "AB123CD", "Ford", "Cargo", "camion", 2018,
                new BigDecimal("400000"), BigDecimal.ZERO, new BigDecimal("400000"), null, null, null, 97.5, 18, 1, 2,
                new VehicleComparisonDto.Review(true, List.of("availability", "recurring_defects")));
        when(service.compare("2026-10-01", "2026-11-06")).thenReturn(new VehicleComparisonDto(
                Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-11-06T15:00:00Z"), List.of(row),
                new VehicleComparisonDto.Averages(null, null, new BigDecimal("400000.00"), null, null, null, 97.5, 18.0,
                        1.0, 2.0)));

        mvc.perform(get("/vehicles/comparison").param("from", "2026-10-01").param("to", "2026-11-06")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicles[0].plate").value("AB123CD"))
                .andExpect(jsonPath("$.vehicles[0].kmDriven").isEmpty())
                .andExpect(jsonPath("$.vehicles[0].maintenanceCostPerKm").isEmpty())
                .andExpect(jsonPath("$.vehicles[0].review.flagged").value(true))
                .andExpect(jsonPath("$.vehicles[0].review.reasons[1]").value("recurring_defects"))
                .andExpect(jsonPath("$.fleet.availabilityPct").value(97.5));
        verifyNoInteractions(vehicleService);
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(get("/vehicles/comparison"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/vehicles/comparison").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/vehicles/comparison").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
