package org.fleetguard.controller;

import org.fleetguard.dto.TireBrandComparisonDto;
import org.fleetguard.dto.TirePositionDto;
import org.fleetguard.dto.VehicleTiresDto;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.TireNotFoundException;
import org.fleetguard.service.TireService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Neumáticos (CAM-157): todo del admin, salvo las posiciones montadas que lee el chofer. */
@WebMvcTest(TireController.class)
class TireControllerTest extends WebMvcTestBase {

    private static final VehicleTiresDto VEHICLE = new VehicleTiresDto("v-1", "AB123CD", "4_ruedas", 100000,
            new BigDecimal("1.6"), List.of(new VehicleTiresDto.Position("del-izq", "Delantera izquierda", 1, "izq", false,
            null)), List.of());

    @MockBean
    private TireService service;

    @Test
    void elAdminVeElVehiculoYRota() throws Exception {
        String admin = loginAs("admin", Role.ADMIN);
        when(service.vehicleTires("v-1")).thenReturn(VEHICLE);
        when(service.rotate(eq("v-1"), any())).thenReturn(VEHICLE);

        mvc.perform(get("/vehicles/v-1/tires").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layout").value("4_ruedas"))
                .andExpect(jsonPath("$.positions[0].label").value("Delantera izquierda"));
        mvc.perform(post("/vehicles/v-1/tires/rotate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\": \"del-izq\", \"to\": \"tras-der\"}").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    @Test
    void laComparacionPorMarcaNoSeConfundeConElIdDeUnaCubierta() throws Exception {
        when(service.brandComparison()).thenReturn(new TireBrandComparisonDto(1000, List.of(
                new TireBrandComparisonDto.Brand("Fate", 1, 0, 10500, new BigDecimal("200000"), new BigDecimal("19.05"), 10500))));

        mvc.perform(get("/tires/brand-comparison").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands[0].costPerKm").value(19.05));
    }

    @Test
    void unaCubiertaInexistenteDa404() throws Exception {
        when(service.get("t-9")).thenThrow(new TireNotFoundException("t-9"));

        mvc.perform(get("/tires/t-9").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TIRE_NOT_FOUND"));
    }

    @Test
    void elChoferSoloLeeLasPosicionesMontadas() throws Exception {
        String chofer = loginAs("chofer1", Role.CHOFER);
        when(service.mountedPositions("v-1")).thenReturn(List.of(new TirePositionDto("del-izq", "Delantera izquierda")));

        mvc.perform(get("/vehicles/v-1/tire-positions").header(HttpHeaders.AUTHORIZATION, chofer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].position").value("del-izq"));
        mvc.perform(get("/vehicles/v-1/tires").header(HttpHeaders.AUTHORIZATION, chofer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/tires").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico1", Role.TECNICO)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/tires")).andExpect(status().isUnauthorized());
    }

    @Test
    void elChoferNoMontaCubiertas() throws Exception {
        mvc.perform(post("/vehicles/v-1/tires/mount").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tireId\": \"t-1\", \"position\": \"del-izq\"}")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
