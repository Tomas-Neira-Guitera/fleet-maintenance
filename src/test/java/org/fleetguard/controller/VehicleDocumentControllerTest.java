package org.fleetguard.controller;

import org.fleetguard.dto.CreateVehicleDocumentRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.RenewVehicleDocumentRequest;
import org.fleetguard.dto.UpdateVehicleDocumentRequest;
import org.fleetguard.dto.VehicleDocumentDto;
import org.fleetguard.dto.VehicleDocumentsDto;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.DocumentNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.service.VehicleDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/vehicles/{id}/documents (CAM-127): solo el admin; forma del JSON y errores. */
@WebMvcTest(VehicleDocumentController.class)
class VehicleDocumentControllerTest extends WebMvcTestBase {

    @MockBean
    private VehicleDocumentService service;

    private static VehicleDocumentDto vtv() {
        return new VehicleDocumentDto("d-1", "v-1", "vtv", "VTV / RTO", null, "VTV / RTO", "0012", "2026-10-20",
                "2026-11-08", null, "por_vencer", 10L, true, null, "2026-10-29T12:00:00Z");
    }

    @Test
    void listaLosDocumentosConSuEstadoYLosObligatoriosQueFaltan() throws Exception {
        when(service.list("v-1")).thenReturn(new VehicleDocumentsDto(List.of(vtv()), List.of(), List.of("seguro"), 15));

        mvc.perform(get("/vehicles/v-1/documents").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("vtv"))
                .andExpect(jsonPath("$.items[0].status").value("por_vencer"))
                .andExpect(jsonPath("$.items[0].daysToExpiry").value(10))
                .andExpect(jsonPath("$.missingRequired[0]").value("seguro"))
                .andExpect(jsonPath("$.leadDays").value(15));
    }

    @Test
    void cargarRenovarCorregirYBorrar() throws Exception {
        String admin = loginAs("admin", Role.ADMIN);
        when(service.create(eq("v-1"), any(CreateVehicleDocumentRequest.class))).thenReturn(vtv());
        when(service.renew(eq("v-1"), eq("d-1"), any(RenewVehicleDocumentRequest.class))).thenReturn(vtv());
        when(service.update(eq("v-1"), eq("d-1"), any(UpdateVehicleDocumentRequest.class))).thenReturn(vtv());

        mvc.perform(post("/vehicles/v-1/documents").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"vtv\",\"expiresAt\":\"2026-11-08\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("d-1"));
        mvc.perform(post("/vehicles/v-1/documents/d-1/renewals").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":\"2027-11-08\"}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/vehicles/v-1/documents/d-1").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":\"\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/vehicles/v-1/documents/d-1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        verify(service).delete("v-1", "d-1");
    }

    @Test
    void erroresConElFormatoDeSiempre() throws Exception {
        String admin = loginAs("admin", Role.ADMIN);
        when(service.create(eq("v-1"), any())).thenThrow(new VehicleValidationException("Datos inválidos",
                List.of(new FieldValidationErrorDetail("expiresAt", "Fecha inválida"))));
        when(service.renew(eq("v-1"), eq("d-9"), any())).thenThrow(new DocumentNotFoundException("d-9"));
        when(service.renew(eq("v-1"), eq("d-2"), any())).thenThrow(new VehicleStateConflictException("DOCUMENT_REPLACED", "Ya se renovó"));

        mvc.perform(post("/vehicles/v-1/documents").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"vtv\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("expiresAt"));
        mvc.perform(post("/vehicles/v-1/documents/d-9/renewals").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_FOUND"));
        mvc.perform(post("/vehicles/v-1/documents/d-2/renewals").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DOCUMENT_REPLACED"));
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(get("/vehicles/v-1/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/vehicles/v-1/documents").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/vehicles/v-1/documents/d-1").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
