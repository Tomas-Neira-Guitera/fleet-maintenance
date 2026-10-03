package org.fleetguard.controller;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.WorkOrderDto;
import org.fleetguard.dto.WorkOrderExpenseDto;
import org.fleetguard.dto.WorkOrderPhotoDto;
import org.fleetguard.exception.WorkOrderConflictException;
import org.fleetguard.exception.WorkOrderNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/work-orders (CAM-14/CAM-15/CAM-60/CAM-62/CAM-63). */
@WebMvcTest(WorkOrderController.class)
class WorkOrderControllerTest extends WebMvcTestBase {

    @MockBean
    private WorkOrderService service;

    private static WorkOrderDto workOrder(String status) {
        return new WorkOrderDto("wo-1", "v-1", "AB123CD", "manual", null, null, null, null, "Cambio de pastillas",
                null, "interno", null, null, "t-1", "tecnico", status, null, List.of(), BigDecimal.ZERO, List.of(),
                "2026-10-03T00:00:00Z", "2026-10-03T00:00:00Z", null);
    }

    @Test
    void crearUnaOtNuevaDevuelve201YReplanificarLaExistenteDevuelve200() throws Exception {
        String body = "{\"sourceType\":\"manual\",\"vehicleId\":\"v-1\",\"title\":\"Frenos\",\"executionType\":\"interno\"}";
        when(service.create(any())).thenReturn(new WorkOrderService.CreateResult(workOrder("asignada"), true));
        mvc.perform(post("/work-orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("asignada"))
                .andExpect(jsonPath("$.technicianUsername").value("tecnico"));

        // CAM-60: una sola OT abierta por origen; si ya existe, se devuelve esa con 200.
        when(service.create(any())).thenReturn(new WorkOrderService.CreateResult(workOrder("asignada"), false));
        mvc.perform(post("/work-orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void elListadoPasaLosFiltrosYDevuelveItems() throws Exception {
        when(service.list("v-1", "en_proceso", null, "t-1")).thenReturn(List.of(workOrder("en_proceso")));

        mvc.perform(get("/work-orders").param("vehicleId", "v-1").param("status", "en_proceso").param("technicianId", "t-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("wo-1"));
    }

    @Test
    void unaOtInexistenteDevuelve404() throws Exception {
        when(service.get("wo-x")).thenThrow(new WorkOrderNotFoundException("wo-x"));

        mvc.perform(get("/work-orders/wo-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WORK_ORDER_NOT_FOUND"));
    }

    @Test
    void unaTransicionDeEstadoInvalidaDevuelve409() throws Exception {
        when(service.update(eq("wo-1"), any())).thenThrow(
                new WorkOrderConflictException("INVALID_STATUS_TRANSITION", "No se puede pasar de 'asignada' a 'finalizada'"));

        mvc.perform(patch("/work-orders/wo-1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"finalizada\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void finalizarSinFotosDevuelve422ConElDetalle() throws Exception {
        when(service.update(eq("wo-1"), any())).thenThrow(new WorkOrderValidationException(
                "Datos inválidos para finalizar la orden de trabajo",
                List.of(new FieldValidationErrorDetail("photos", "Hace falta al menos una foto"))));

        mvc.perform(patch("/work-orders/wo-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"finalizada\",\"closingDescription\":\"Listo\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("photos"));
    }

    @Test
    void gastosYFotosSeCreanCon201YSeBorranCon204() throws Exception {
        when(service.addExpense(eq("wo-1"), any())).thenReturn(
                new WorkOrderExpenseDto("e-1", "repuesto", "Pastillas", new BigDecimal("45000.50"), "2026-10-03T00:00:00Z"));
        when(service.addPhoto(eq("wo-1"), any())).thenReturn(
                new WorkOrderPhotoDto("p-1", "http://localhost/f.jpg", "2026-10-03T00:00:00Z"));

        mvc.perform(post("/work-orders/wo-1/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"repuesto\",\"description\":\"Pastillas\",\"amount\":45000.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(45000.50));
        mvc.perform(post("/work-orders/wo-1/photos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoUrl\":\"http://localhost/f.jpg\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/work-orders/wo-1/expenses/e-1")).andExpect(status().isNoContent());
        mvc.perform(delete("/work-orders/wo-1/photos/p-1")).andExpect(status().isNoContent());

        verify(service).deleteExpense("wo-1", "e-1");
        verify(service).deletePhoto("wo-1", "p-1");
    }
}
