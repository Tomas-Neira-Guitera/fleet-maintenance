package org.fleetguard.controller;

import org.fleetguard.dto.AssignmentDto;
import org.fleetguard.dto.AssignmentSummaryDto;
import org.fleetguard.dto.CompletionDto;
import org.fleetguard.dto.CompletionResultDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.MaintenancePlanDto;
import org.fleetguard.dto.ScheduleDto;
import org.fleetguard.exception.AssignmentNotFoundException;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenancePlanNotFoundException;
import org.fleetguard.exception.MaintenanceValidationException;
import org.fleetguard.exception.ScheduleNotFoundException;
import org.fleetguard.service.MaintenanceCompletionService;
import org.fleetguard.service.MaintenancePlanService;
import org.fleetguard.service.ScheduledMaintenanceService;
import org.fleetguard.service.VehicleMaintenanceAssignmentService;
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

/**
 * Mantenimiento preventivo por HTTP: catálogo de planes (/maintenance-plans), asignaciones y
 * completions de un vehículo (/vehicles/{id}/maintenance-assignments) y el calendario
 * (/maintenance-schedule). Ver CAM-40-maintenance-api-contract.md y CAM-42-schedule-contract.md.
 */
@WebMvcTest({MaintenancePlanController.class, VehicleMaintenanceController.class, ScheduledMaintenanceController.class})
class MaintenanceControllersTest extends WebMvcTestBase {

    @MockBean
    private MaintenancePlanService planService;

    @MockBean
    private VehicleMaintenanceAssignmentService assignmentService;

    @MockBean
    private MaintenanceCompletionService completionService;

    @MockBean
    private ScheduledMaintenanceService scheduleService;

    private static MaintenancePlanDto planDto() {
        return new MaintenancePlanDto("p-1", "Cambio de aceite", "motor", "km", 10000, null, true);
    }

    private static AssignmentDto assignmentDto() {
        return new AssignmentDto("a-1", "v-1", "p-1", "Cambio de aceite", "km", 40000L, null, 50000L, null, "por_vencer", true);
    }

    private static ScheduleDto scheduleDto() {
        return new ScheduleDto("s-1", "v-1", "AB123CD", "manual", null, null, "Frenos", "2026-10-06T13:00:00Z",
                "scheduled", null, null);
    }

    // --- Catálogo de planes ---

    @Test
    void elCatalogoSeListaYSeCreaCon201() throws Exception {
        when(planService.list(true, "motor")).thenReturn(List.of(planDto()));
        when(planService.create(any())).thenReturn(planDto());

        mvc.perform(get("/maintenance-plans").param("category", "motor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].intervalType").value("km"));
        mvc.perform(post("/maintenance-plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cambio de aceite\",\"intervalType\":\"km\",\"intervalKm\":10000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("p-1"));
    }

    @Test
    void unPlanEnUsoNoSeBorraYUnoInexistenteDa404() throws Exception {
        doThrow(new MaintenanceConflictException("PLAN_IN_USE", "El plan tiene asignaciones")).when(planService).delete("p-1");
        doThrow(new MaintenancePlanNotFoundException("p-x")).when(planService).delete("p-x");

        mvc.perform(delete("/maintenance-plans/p-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PLAN_IN_USE"));
        mvc.perform(delete("/maintenance-plans/p-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MAINTENANCE_PLAN_NOT_FOUND"));
    }

    @Test
    void editarUnPlanConIntervaloIncoherenteDevuelve422() throws Exception {
        when(planService.update(eq("p-1"), any())).thenThrow(new MaintenanceValidationException("Datos inválidos para editar el plan",
                List.of(new FieldValidationErrorDetail("intervalDays", "Obligatorio y > 0 para intervalType both"))));

        mvc.perform(patch("/maintenance-plans/p-1").contentType(MediaType.APPLICATION_JSON).content("{\"intervalType\":\"both\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].field").value("intervalDays"));
    }

    // --- Asignaciones y completions ---

    @Test
    void asignarUnPlanDevuelve201ConElVencimientoCalculado() throws Exception {
        when(assignmentService.create(eq("v-1"), any())).thenReturn(assignmentDto());
        when(assignmentService.list("v-1", false)).thenReturn(List.of());

        mvc.perform(post("/vehicles/v-1/maintenance-assignments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenancePlanId\":\"p-1\",\"lastDoneKm\":40000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextDueKm").value(50000))
                .andExpect(jsonPath("$.status").value("por_vencer"));
        mvc.perform(get("/vehicles/v-1/maintenance-assignments").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void asignarDosVecesElMismoPlanDevuelve409() throws Exception {
        when(assignmentService.create(eq("v-1"), any())).thenThrow(
                new MaintenanceConflictException("DUPLICATE_ACTIVE_ASSIGNMENT", "Este vehículo ya tiene una asignación activa de este plan"));

        mvc.perform(post("/vehicles/v-1/maintenance-assignments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenancePlanId\":\"p-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_ACTIVE_ASSIGNMENT"));
    }

    @Test
    void editarYDesasignarUnaAsignacion() throws Exception {
        when(assignmentService.update(eq("v-1"), eq("a-1"), any())).thenReturn(assignmentDto());
        doThrow(new AssignmentNotFoundException("a-x")).when(assignmentService).delete("v-1", "a-x");

        mvc.perform(patch("/vehicles/v-1/maintenance-assignments/a-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastDoneKm\":45000}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/vehicles/v-1/maintenance-assignments/a-1")).andExpect(status().isNoContent());
        mvc.perform(delete("/vehicles/v-1/maintenance-assignments/a-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ASSIGNMENT_NOT_FOUND"));
        verify(assignmentService).delete("v-1", "a-1");
    }

    @Test
    void registrarUnMantenimientoDevuelve201ConLaAsignacionActualizada() throws Exception {
        when(completionService.create(eq("v-1"), eq("a-1"), any())).thenReturn(new CompletionResultDto("c-1", "a-1",
                "2026-10-03", 51000L, null, null, new AssignmentSummaryDto("a-1", 61000L, null, "al_dia")));
        when(completionService.list("v-1", "a-1")).thenReturn(List.of(new CompletionDto("c-1", "2026-10-03", 51000L, null, null)));

        mvc.perform(post("/vehicles/v-1/maintenance-assignments/a-1/completions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedAt\":\"2026-10-03\",\"completedKm\":51000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updatedAssignment.nextDueKm").value(61000))
                .andExpect(jsonPath("$.updatedAssignment.status").value("al_dia"));
        mvc.perform(get("/vehicles/v-1/maintenance-assignments/a-1/completions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].completedKm").value(51000));
    }

    // --- Calendario ---

    @Test
    void programarDevuelve201SiEsNuevaY200SiMueveLaExistente() throws Exception {
        String body = "{\"sourceType\":\"assignment\",\"sourceId\":\"a-1\",\"scheduledAt\":\"2026-10-06T13:00:00Z\"}";
        when(scheduleService.create(any())).thenReturn(new ScheduledMaintenanceService.CreateResult(scheduleDto(), true));
        mvc.perform(post("/maintenance-schedule").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        when(scheduleService.create(any())).thenReturn(new ScheduledMaintenanceService.CreateResult(scheduleDto(), false));
        mvc.perform(post("/maintenance-schedule").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scheduled"));
    }

    @Test
    void elCalendarioExigeElRangoDeFechas() throws Exception {
        when(scheduleService.listByRange("2026-10-05", "2026-10-11", null, "v-1")).thenReturn(List.of(scheduleDto()));

        mvc.perform(get("/maintenance-schedule").param("from", "2026-10-05").param("to", "2026-10-11").param("vehicleId", "v-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].plate").value("AB123CD"));
        // Sin "to": 400 que nombra el parámetro, no un 500.
        mvc.perform(get("/maintenance-schedule").param("from", "2026-10-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parámetro obligatorio 'to'."));
    }

    @Test
    void cancelarUnaProgramacionConOtEnCursoDevuelve409ConSuCodigo() throws Exception {
        when(scheduleService.update(eq("s-1"), any())).thenThrow(new MaintenanceConflictException("WORK_ORDER_IN_PROGRESS",
                "Esta programación tiene una OT en curso asignada a tecnico. ¿Cancelar las dos?"));
        when(scheduleService.update(eq("s-x"), any())).thenThrow(new ScheduleNotFoundException("s-x"));

        // CAM-77: el frontend usa este código para pedir la confirmación.
        mvc.perform(patch("/maintenance-schedule/s-1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"cancelled\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("WORK_ORDER_IN_PROGRESS"));
        mvc.perform(patch("/maintenance-schedule/s-x").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SCHEDULE_NOT_FOUND"));
    }
}
