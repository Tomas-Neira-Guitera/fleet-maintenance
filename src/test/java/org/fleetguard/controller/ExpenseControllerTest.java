package org.fleetguard.controller;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/expenses (CAM-52): solo lectura y solo para el admin. */
@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest extends WebMvcTestBase {

    @MockBean
    private ExpenseService service;

    @Test
    void elAdminVeLosGastosConLosFiltrosQuePidio() throws Exception {
        when(service.list("v-1", "repuesto", "2026-01-01", "2026-12-31")).thenReturn(List.of(new ExpenseDto("e-1",
                "2026-03-10T13:00:00Z", "v-1", "AB123CD", "repuesto", "Aceite 10W40", new BigDecimal("120000.50"),
                "wo-1", "Cambio de aceite", "finalizada", "plan", "interno")));

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
        when(service.list(null, null, null, null)).thenReturn(List.of());

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
        when(service.list(null, null, "ayer", null)).thenThrow(new WorkOrderValidationException("Período inválido",
                List.of(new FieldValidationErrorDetail("from", "Fecha inválida, formato esperado ISO-8601"))));

        mvc.perform(get("/expenses").param("from", "ayer").header(HttpHeaders.AUTHORIZATION, loginAs("admin", Role.ADMIN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("from"));
    }
}
