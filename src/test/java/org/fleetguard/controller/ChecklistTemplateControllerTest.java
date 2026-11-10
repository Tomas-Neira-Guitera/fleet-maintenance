package org.fleetguard.controller;

import org.fleetguard.dto.ApplyChecklistTemplateResultDto;
import org.fleetguard.dto.ChecklistTemplateDto;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.ChecklistTemplateNotFoundException;
import org.fleetguard.service.ChecklistTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/checklist-templates (CAM-150): ABM y aplicar, solo para el admin. */
@WebMvcTest(ChecklistTemplateController.class)
class ChecklistTemplateControllerTest extends WebMvcTestBase {

    private static final String BODY = """
            {"name": "Frigorífico", "items": [{"label": "Temperatura de la cámara", "type": "number", "section": "interior"}]}
            """;

    private static final ChecklistTemplateDto TEMPLATE = new ChecklistTemplateDto("t-1", "Frigorífico",
            List.of(new ChecklistTemplateDto.Item("i-1", "Temperatura de la cámara", "number", "interior")),
            "2026-11-10T03:00:00Z");

    @MockBean
    private ChecklistTemplateService service;

    @Test
    void listaYCreaPlantillas() throws Exception {
        String admin = loginAs("admin", Role.ADMIN);
        when(service.list()).thenReturn(List.of(TEMPLATE));
        when(service.create(any())).thenReturn(TEMPLATE);

        mvc.perform(get("/checklist-templates").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Frigorífico"))
                .andExpect(jsonPath("$.items[0].items[0].type").value("number"));
        mvc.perform(post("/checklist-templates").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("t-1"));
    }

    @Test
    void editaYBorra() throws Exception {
        String admin = loginAs("admin", Role.ADMIN);
        when(service.update(eq("t-1"), any())).thenReturn(TEMPLATE);

        mvc.perform(put("/checklist-templates/t-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mvc.perform(delete("/checklist-templates/t-1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        verify(service).delete("t-1");
    }

    @Test
    void aplicarDevuelveLoAgregadoYLoSalteadoPorVehiculo() throws Exception {
        when(service.apply(eq("t-1"), any())).thenReturn(new ApplyChecklistTemplateResultDto("t-1", 1, 1, List.of(
                new ApplyChecklistTemplateResultDto.VehicleResult("v-1", "AA111AA", 1, 0, List.of()),
                new ApplyChecklistTemplateResultDto.VehicleResult("v-2", "BB222BB", 0, 1,
                        List.of("Temperatura de la cámara")))));

        mvc.perform(post("/checklist-templates/t-1/apply").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleIds\": [\"v-1\", \"v-2\"]}")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.vehicles[1].skippedLabels[0]").value("Temperatura de la cámara"));
    }

    @Test
    void unaPlantillaInexistenteDa404() throws Exception {
        doThrow(new ChecklistTemplateNotFoundException("t-9")).when(service).delete("t-9");

        mvc.perform(delete("/checklist-templates/t-9").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHECKLIST_TEMPLATE_NOT_FOUND"));
    }

    @Test
    void sinTokenDa401YUnChoferOTecnicoReciben403() throws Exception {
        mvc.perform(get("/checklist-templates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/checklist-templates").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/checklist-templates/t-1/apply").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleIds\": [\"v-1\"]}")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("tecnico1", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
