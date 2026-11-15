package org.fleetguard.controller;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.UpdateWorkOrderRequest;
import org.fleetguard.dto.WorkOrderDto;
import org.fleetguard.dto.WorkOrderExpenseDto;
import org.fleetguard.dto.WorkOrderPauseDto;
import org.fleetguard.dto.WorkOrderPhotoDto;
import org.fleetguard.exception.WorkOrderConflictException;
import org.fleetguard.exception.WorkOrderNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.dto.UserRefDto;
import org.fleetguard.dto.WorkOrderCommentDto;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.ForbiddenException;
import org.fleetguard.service.WorkOrderCommentService;
import org.fleetguard.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

/** /api/work-orders (CAM-14/CAM-15/CAM-60/CAM-62/CAM-63). */
@WebMvcTest(WorkOrderController.class)
class WorkOrderControllerTest extends WebMvcTestBase {

    @MockBean
    private WorkOrderService service;

    @MockBean
    private WorkOrderCommentService commentService;

    private static WorkOrderDto workOrder(String status) {
        return new WorkOrderDto("wo-1", "v-1", "AB123CD", "manual", null, null, null, null, "Cambio de pastillas",
                null, "interno", null, null, "t-1", "tecnico", status, null, List.of(), BigDecimal.ZERO, List.of(),
                "2026-10-03T00:00:00Z", "2026-10-03T00:00:00Z", null, null, 0);
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
    void unKilometrajeConDecimalesLlegaAlServiceSinTruncar() throws Exception {
        when(service.update(eq("wo-1"), any())).thenReturn(workOrder("finalizada"));

        mvc.perform(patch("/work-orders/wo-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"finalizada\",\"closingDescription\":\"Listo\",\"completedKm\":1500.7}"))
                .andExpect(status().isOk());

        // CAM-74: con Long, Jackson lo convertía en 1500 sin avisar y el service no podía rechazarlo.
        ArgumentCaptor<UpdateWorkOrderRequest> captor = ArgumentCaptor.forClass(UpdateWorkOrderRequest.class);
        verify(service).update(eq("wo-1"), captor.capture());
        assertEquals(new BigDecimal("1500.7"), captor.getValue().completedKm());
    }

    @Test
    void pausarLlegaConElMotivoYDevuelveLaPausaYElTiempo() throws Exception {
        // CAM-148: el record tiene dos constructores; Jackson tiene que usar el que trae la pausa.
        WorkOrderDto onHold = new WorkOrderDto("wo-1", "v-1", "AB123CD", "manual", null, null, null, null,
                "Cambio de pastillas", null, "interno", null, null, "t-1", "tecnico", "en_espera", null, List.of(),
                BigDecimal.ZERO, List.of(), "2026-10-03T00:00:00Z", "2026-10-03T00:00:00Z", null,
                new WorkOrderPauseDto("esperando_repuesto", "Pastillas pedidas", "2026-10-03T10:00:00Z"), 7200);
        when(service.update(eq("wo-1"), any())).thenReturn(onHold);

        mvc.perform(patch("/work-orders/wo-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"en_espera\",\"pauseReason\":\"esperando_repuesto\",\"pauseComment\":\"Pastillas pedidas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("en_espera"))
                .andExpect(jsonPath("$.pause.reason").value("esperando_repuesto"))
                .andExpect(jsonPath("$.pause.comment").value("Pastillas pedidas"))
                .andExpect(jsonPath("$.pausedSeconds").value(7200));

        ArgumentCaptor<UpdateWorkOrderRequest> captor = ArgumentCaptor.forClass(UpdateWorkOrderRequest.class);
        verify(service).update(eq("wo-1"), captor.capture());
        assertEquals("en_espera", captor.getValue().status());
        assertEquals("esperando_repuesto", captor.getValue().pauseReason());
        assertEquals("Pastillas pedidas", captor.getValue().pauseComment());
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
    void unGastoLlegaConElNumeroYLaFotoDelComprobanteYLosDevuelve() throws Exception {
        String receipt = "http://localhost/api/photos/abc";
        when(service.addExpense(eq("wo-1"), argThat(request -> receipt.equals(request.receiptPhotoUrl())
                && "0001-00012345".equals(request.receiptNumber())))).thenReturn(
                new WorkOrderExpenseDto("e-1", "otro", "Grúa", BigDecimal.TEN, "2026-10-03T00:00:00Z", "0001-00012345", receipt));

        mvc.perform(post("/work-orders/wo-1/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"otro\",\"description\":\"Grúa\",\"amount\":10,"
                                + "\"receiptNumber\":\"0001-00012345\",\"receiptPhotoUrl\":\"" + receipt + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").value("0001-00012345"))
                .andExpect(jsonPath("$.receiptPhotoUrl").value(receipt));
    }

    @Test
    void gastosYFotosSeCreanCon201YSeBorranCon204() throws Exception {
        when(service.addExpense(eq("wo-1"), any())).thenReturn(
                new WorkOrderExpenseDto("e-1", "repuesto", "Pastillas", new BigDecimal("45000.50"), "2026-10-03T00:00:00Z", null, null));
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

    // --- CAM-149: comentarios ---

    @Test
    void elTecnicoComentaYDevuelve201ConElAutorDelToken() throws Exception {
        when(commentService.add(eq("wo-1"), any(), argThat(u -> u.role() == Role.TECNICO && u.username().equals("javier"))))
                .thenReturn(new WorkOrderCommentDto("c-1", new UserRefDto("t-1", "javier"), "TECNICO", "¿Lo cambio?",
                        "2026-11-13T12:00:00Z"));

        mvc.perform(post("/work-orders/wo-1/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"¿Lo cambio?\"}")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("javier", Role.TECNICO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.name").value("javier"))
                .andExpect(jsonPath("$.authorRole").value("TECNICO"))
                .andExpect(jsonPath("$.text").value("¿Lo cambio?"));
    }

    @Test
    void elAdminVeLosComentariosEnUnaLista() throws Exception {
        when(commentService.list(eq("wo-1"), any())).thenReturn(List.of(
                new WorkOrderCommentDto("c-1", new UserRefDto("t-1", "javier"), "TECNICO", "¿Lo cambio?", "2026-11-13T12:00:00Z")));

        mvc.perform(get("/work-orders/wo-1/comments").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].text").value("¿Lo cambio?"));
    }

    @Test
    void losComentariosSinTokenSon401ConRolDeChofer403YDeOtroTecnico403() throws Exception {
        mvc.perform(get("/work-orders/wo-1/comments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/work-orders/wo-1/comments").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/work-orders/wo-1/comments").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(commentService);

        when(commentService.list(eq("wo-1"), any())).thenThrow(new ForbiddenException());
        mvc.perform(get("/work-orders/wo-1/comments").header(HttpHeaders.AUTHORIZATION, loginAs("otro", Role.TECNICO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void comentarUnaOtCerradaEs409() throws Exception {
        when(commentService.add(eq("wo-1"), any(), any())).thenThrow(new WorkOrderConflictException("WORK_ORDER_CLOSED",
                "No se pueden agregar comentarios a una orden de trabajo finalizada o cancelada"));

        mvc.perform(post("/work-orders/wo-1/comments").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("WORK_ORDER_CLOSED"));
    }
}
