package org.fleetguard.controller;

import org.fleetguard.dto.FuelLoadDto;
import org.fleetguard.dto.UserRefDto;
import org.fleetguard.dto.VehicleFuelDto;
import org.fleetguard.entity.Role;
import org.fleetguard.service.FuelLoadService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Combustible (CAM-156): el chofer y el admin cargan; editar, borrar y ver el consumo es del admin. */
@WebMvcTest(FuelLoadController.class)
class FuelLoadControllerTest extends WebMvcTestBase {

    private static final String BODY = """
            {"vehicleId": "v-1", "liters": 85.5, "amount": 120000, "odometerKm": 152300, "fullTank": true}
            """;

    private static final FuelLoadDto LOAD = new FuelLoadDto("f-1", "v-1", "AB123CD", "2026-11-10T15:00:00Z",
            new BigDecimal("85.5"), new BigDecimal("120000"), 152300, true, new UserRefDto("u-1", "chofer1"),
            new FuelLoadDto.Consumption(500, new BigDecimal("150"), new BigDecimal("30.00"), new BigDecimal("360.00")),
            null);

    @MockBean
    private FuelLoadService service;

    @Test
    void elChoferYElAdminCargan() throws Exception {
        when(service.create(any(), any())).thenReturn(LOAD);

        mvc.perform(post("/fuel-loads").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consumption.litersPer100Km").value(30.00))
                .andExpect(jsonPath("$.createdBy.name").value("chofer1"));
        mvc.perform(post("/fuel-loads").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isCreated());
    }

    @Test
    void elAdminEditaBorraYVeElConsumo() throws Exception {
        String admin = loginAs("admin", Role.ADMIN);
        when(service.update(eq("f-1"), any())).thenReturn(LOAD);
        when(service.vehicleFuel("v-1", 6)).thenReturn(new VehicleFuelDto("v-1", "AB123CD", 6,
                new VehicleFuelDto.Summary(1, new BigDecimal("85.5"), new BigDecimal("120000"), 500,
                        new BigDecimal("30.00"), new BigDecimal("360.00")), List.of(), List.of(LOAD)));

        mvc.perform(put("/fuel-loads/f-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mvc.perform(delete("/fuel-loads/f-1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        verify(service).delete("f-1");
        mvc.perform(get("/vehicles/v-1/fuel").param("months", "6").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.measuredKm").value(500))
                .andExpect(jsonPath("$.loads[0].id").value("f-1"));
    }

    @Test
    void elChoferNoEditaNiVeElConsumoYElTecnicoNoCarga() throws Exception {
        String chofer = loginAs("chofer1", Role.CHOFER);
        mvc.perform(put("/fuel-loads/f-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, chofer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/vehicles/v-1/fuel").header(HttpHeaders.AUTHORIZATION, chofer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/fuel-loads").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("tecnico1", Role.TECNICO)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/fuel-loads").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
