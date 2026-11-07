package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.RoadDefectRequest;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.service.RoadDefectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/vehicles/{id}/road-defects (CAM-139): solo el chofer. */
@WebMvcTest(RoadDefectController.class)
class RoadDefectControllerTest extends WebMvcTestBase {

    private static final String BODY = """
            {"itemId":"ext-luces","defect":{"severity":"non-blocking","description":"Luz de giro"}}""";

    @MockBean
    private RoadDefectService service;

    @Test
    void elChoferReportaYRecibeElDefectoCon201() throws Exception {
        when(service.report(eq("v-1"), any(), any())).thenReturn(new DefectDto("d-1", "non-blocking", "Luz de giro",
                null, "2026-11-06T12:00:00Z", "v-1", "AB123CD", "open", "chofer1", null, "on-road"));

        mvc.perform(post("/vehicles/v-1/road-defects").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("d-1"))
                .andExpect(jsonPath("$.source").value("on-road"));

        ArgumentCaptor<RoadDefectRequest> request = ArgumentCaptor.forClass(RoadDefectRequest.class);
        ArgumentCaptor<AuthenticatedUser> user = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(service).report(eq("v-1"), request.capture(), user.capture());
        assertEquals("ext-luces", request.getValue().itemId());
        assertEquals("Luz de giro", request.getValue().defect().description());
        assertEquals("chofer1", user.getValue().username());
    }

    @Test
    void elViajeDeOtroChoferDevuelve409() throws Exception {
        when(service.report(eq("v-1"), any(), any())).thenThrow(
                new VehicleStateConflictException("NOT_YOUR_TRIP", "Solo puede reportar el chofer que tiene el viaje abierto."));

        mvc.perform(post("/vehicles/v-1/road-defects").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_YOUR_TRIP"));
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(post("/vehicles/v-1/road-defects").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/vehicles/v-1/road-defects").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/vehicles/v-1/road-defects").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
