package org.fleetguard.controller;

import org.fleetguard.dto.ChecklistItemDto;
import org.fleetguard.dto.VehicleChecklistItemDto;
import org.fleetguard.exception.ChecklistItemNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.service.VehicleChecklistService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checklist configurable por vehículo (CAM-31): lo que ve el chofer y la configuración del admin. */
@WebMvcTest(VehicleChecklistController.class)
class VehicleChecklistControllerTest extends WebMvcTestBase {

    @MockBean
    private VehicleChecklistService service;

    private static VehicleChecklistItemDto extra(boolean enabled) {
        return new VehicleChecklistItemDto("extra-1", "Faja de sujeción", "check", "exterior", false, "extra", enabled, false);
    }

    @Test
    void elChoferRecibeElChecklistDelVehiculo() throws Exception {
        when(service.getChecklist("v-1", "pre-trip")).thenReturn(List.of(
                new ChecklistItemDto("int-km", "Kilómetros actuales", "number", "interior", true)));

        mvc.perform(get("/vehicles/v-1/checklist").param("type", "pre-trip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("int-km"))
                .andExpect(jsonPath("$.items[0].required").value(true));
    }

    @Test
    void elAdminVeLaConfiguracionYAgregaItemsCon201() throws Exception {
        when(service.getConfig("v-1")).thenReturn(List.of(extra(true)));
        when(service.addExtra(eq("v-1"), any())).thenReturn(extra(false));

        mvc.perform(get("/vehicles/v-1/checklist-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].origin").value("extra"));
        mvc.perform(post("/vehicles/v-1/checklist-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Faja de sujeción\",\"section\":\"exterior\",\"enabled\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void quitarElKilometrajeDevuelve409PorqueEsObligatorio() throws Exception {
        when(service.setEnabled(eq("v-1"), eq("int-km"), any())).thenThrow(
                new VehicleStateConflictException("ITEM_LOCKED", "\"Kilómetros actuales\" es obligatorio y no se puede quitar del checklist"));

        mvc.perform(patch("/vehicles/v-1/checklist-items/int-km").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEM_LOCKED"));
    }

    @Test
    void eliminarUnItemAgregadoDevuelve204YUnoInexistente404() throws Exception {
        doThrow(new ChecklistItemNotFoundException("extra-x")).when(service).deleteExtra("v-1", "extra-x");

        mvc.perform(delete("/vehicles/v-1/checklist-items/extra-1")).andExpect(status().isNoContent());
        mvc.perform(delete("/vehicles/v-1/checklist-items/extra-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHECKLIST_ITEM_NOT_FOUND"));
        verify(service).deleteExtra("v-1", "extra-1");
    }
}
