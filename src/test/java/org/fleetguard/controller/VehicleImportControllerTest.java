package org.fleetguard.controller;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.VehicleImportResultDto;
import org.fleetguard.entity.Role;
import org.fleetguard.service.VehicleImportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/vehicles/import (CAM-154): vista previa e importación, solo para el admin. */
@WebMvcTest(VehicleImportController.class)
class VehicleImportControllerTest extends WebMvcTestBase {

    private static final String BODY = """
            {"rows": [{"plate": "AB123CD", "brand": "Ford", "model": "Cargo", "year": 2019, "odometerKm": 120000},
                      {"plate": "", "brand": "Fiat", "model": "Ducato"}]}
            """;

    @MockBean
    private VehicleImportService service;

    @Test
    void laVistaPreviaDevuelveCadaFilaConSusErrores() throws Exception {
        when(service.importVehicles(any(), eq(true))).thenReturn(new VehicleImportResultDto(2, 1, 0, List.of(
                new VehicleImportResultDto.Row(1, "AB123CD", true, List.of(), null),
                new VehicleImportResultDto.Row(2, null, false,
                        List.of(new FieldValidationErrorDetail("plate", "La patente es obligatoria")), null))));

        mvc.perform(post("/vehicles/import").param("dryRun", "true").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY).header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(1))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.rows[1].valid").value(false))
                .andExpect(jsonPath("$.rows[1].errors[0].field").value("plate"));
    }

    @Test
    void sinDryRunImporta() throws Exception {
        when(service.importVehicles(any(), eq(false))).thenReturn(new VehicleImportResultDto(1, 1, 1,
                List.of(new VehicleImportResultDto.Row(1, "AB123CD", true, List.of(), "v-1"))));

        mvc.perform(post("/vehicles/import").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.rows[0].vehicleId").value("v-1"));
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(post("/vehicles/import").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/vehicles/import").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
