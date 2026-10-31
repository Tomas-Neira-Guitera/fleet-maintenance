package org.fleetguard.controller;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.VehicleExpenseRequest;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.ExpenseNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
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

/** /api/expenses (CAM-52, CAM-109): el listado y el alta, edición y baja de gastos operativos, solo para el admin. */
@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest extends WebMvcTestBase {

    private static final String BODY = """
            {"vehicleId":"v-1","date":"2026-10-05","category":"combustible","description":"Gasoil","amount":45000.50}""";

    @MockBean
    private ExpenseService service;

    @Test
    void elAdminVeLosGastosConLosFiltrosQuePidio() throws Exception {
        when(service.list("v-1", "repuesto", null, "2026-01-01", "2026-12-31")).thenReturn(List.of(new ExpenseDto("e-1",
                "2026-03-10T13:00:00Z", "v-1", "AB123CD", "repuesto", "Aceite 10W40", new BigDecimal("120000.50"),
                "wo-1", "Cambio de aceite", "finalizada", "plan", "interno", null, null)));

        mvc.perform(get("/expenses").param("vehicleId", "v-1").param("category", "repuesto")
                        .param("from", "2026-01-01").param("to", "2026-12-31")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].plate").value("AB123CD"))
                .andExpect(jsonPath("$.items[0].amount").value(120000.50))
                .andExpect(jsonPath("$.items[0].workOrderId").value("wo-1"))
                .andExpect(jsonPath("$.items[0].origin").value("plan"));
    }

    @Test
    void sinGastosDevuelveUnaListaVaciaYNoUnError() throws Exception {
        when(service.list(null, null, null, null, null)).thenReturn(List.of());

        mvc.perform(get("/expenses").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void sinTokenDevuelve401YConOtroRol403() throws Exception {
        mvc.perform(get("/expenses"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/expenses").header(HttpHeaders.AUTHORIZATION, loginAs("chofer1", Role.CHOFER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/expenses").header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unPeriodoInvalidoDevuelve422ConElCampo() throws Exception {
        when(service.list(null, null, null, "ayer", null)).thenThrow(new WorkOrderValidationException("Período inválido",
                List.of(new FieldValidationErrorDetail("from", "Fecha inválida, formato esperado ISO-8601"))));

        mvc.perform(get("/expenses").param("from", "ayer").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("from"));
    }

    private static ExpenseDto operating() {
        return new ExpenseDto("e-2", "2026-10-05T03:00:00Z", "v-1", "AB123CD", "combustible", "Gasoil",
                new BigDecimal("45000.50"), null, null, null, "operating", null, null, null);
    }

    @Test
    void elAdminCargaUnGastoOperativoYRecibe201() throws Exception {
        when(service.create(any())).thenReturn(operating());

        mvc.perform(post("/expenses").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("operating"))
                .andExpect(jsonPath("$.workOrderId").value(nullValue()));
        verify(service).create(new VehicleExpenseRequest("v-1", "2026-10-05", "combustible", "Gasoil",
                new BigDecimal("45000.50")));
    }

    @Test
    void elAdminEditaYBorraUnGastoOperativo() throws Exception {
        when(service.update(eq("e-2"), any())).thenReturn(operating());

        mvc.perform(put("/expenses/e-2").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("e-2"));
        mvc.perform(delete("/expenses/e-2").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isNoContent());
        verify(service).delete("e-2");
    }

    @Test
    void unGastoQueNoEsOperativoDevuelve404() throws Exception {
        doThrow(new ExpenseNotFoundException("e-ot")).when(service).delete("e-ot");

        mvc.perform(delete("/expenses/e-ot").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EXPENSE_NOT_FOUND"));
    }

    @Test
    void cargarEditarYBorrarExigenElTokenDeUnAdmin() throws Exception {
        List<MockHttpServletRequestBuilder> writes = List.of(
                post("/expenses").contentType(MediaType.APPLICATION_JSON).content(BODY),
                put("/expenses/e-2").contentType(MediaType.APPLICATION_JSON).content(BODY),
                delete("/expenses/e-2"));

        for (MockHttpServletRequestBuilder write : writes) {
            mvc.perform(write).andExpect(status().isUnauthorized());
            mvc.perform(write.header(HttpHeaders.AUTHORIZATION, loginAs("tecnico", Role.TECNICO)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
}
