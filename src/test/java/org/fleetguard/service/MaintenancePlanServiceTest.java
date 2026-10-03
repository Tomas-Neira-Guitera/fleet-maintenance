package org.fleetguard.service;

import org.fleetguard.dto.CreateMaintenancePlanRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.MaintenancePlanDto;
import org.fleetguard.dto.UpdateMaintenancePlanRequest;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenancePlanNotFoundException;
import org.fleetguard.exception.MaintenanceValidationException;
import org.fleetguard.mapper.MaintenancePlanMapper;
import org.fleetguard.repository.MaintenancePlanRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Catálogo de planes de mantenimiento (CAM-16/CAM-40). Lo que importa proteger: que un plan
 * nunca quede con un intervalo incoherente con su tipo (de eso depende todo el cálculo de
 * vencimientos), y que un plan en uso no se pueda borrar.
 */
class MaintenancePlanServiceTest {

    private final MaintenancePlanRepository planRepository = mock(MaintenancePlanRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final MaintenancePlanService service =
            new MaintenancePlanService(planRepository, assignmentRepository, new MaintenancePlanMapper());

    private static MaintenancePlan plan(IntervalType type, Integer km, Integer days) throws Exception {
        MaintenancePlan plan = new MaintenancePlan("Cambio de aceite", "motor", type, km, days);
        Field field = MaintenancePlan.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(plan, UUID.randomUUID());
        return plan;
    }

    private MaintenancePlan stored(MaintenancePlan plan) {
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(planRepository.save(plan)).thenReturn(plan);
        return plan;
    }

    private void saveReturnsWithId() {
        when(planRepository.save(any(MaintenancePlan.class))).thenAnswer(invocation -> {
            MaintenancePlan saved = invocation.getArgument(0);
            Field field = MaintenancePlan.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(saved, UUID.randomUUID());
            return saved;
        });
    }

    private static List<String> fields(MaintenanceValidationException ex) {
        return ex.getDetails().stream().map(FieldValidationErrorDetail::field).toList();
    }

    @Test
    void altaDeUnPlanPorKilometrajeRecortaElNombre() {
        saveReturnsWithId();

        MaintenancePlanDto created = service.create(
                new CreateMaintenancePlanRequest("  Cambio de aceite ", "motor", "km", 10000, null));

        assertEquals("Cambio de aceite", created.name());
        assertEquals("km", created.intervalType());
        assertEquals(10000, created.intervalKm());
        assertTrue(created.active());
    }

    @Test
    void unPlanPorKilometrajeExigeIntervalKmPositivoYNoAceptaDias() {
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateMaintenancePlanRequest("Aceite", null, "km", 0, 180)));

        assertEquals(List.of("intervalKm", "intervalDays"), fields(ex));
        verify(planRepository, never()).save(any());
    }

    @Test
    void unPlanPorKmOTiempoExigeLosDosIntervalos() {
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateMaintenancePlanRequest("Frenos", null, "both", 20000, null)));

        assertEquals(List.of("intervalDays"), fields(ex));
    }

    @Test
    void unPlanPorTiempoNoAceptaKilometraje() {
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateMaintenancePlanRequest("VTV", null, "time", 5000, 365)));

        assertEquals(List.of("intervalKm"), fields(ex));
    }

    @Test
    void altaSinNombreConNombreLargoOTipoInvalidoDevuelve422() {
        assertEquals(List.of("name", "intervalType"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateMaintenancePlanRequest(" ", null, "semanal", null, null)))));
        // CAM-79: el nombre se ve en el calendario, hasta 30 caracteres.
        assertEquals(List.of("name"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateMaintenancePlanRequest("a".repeat(31), null, "km", 1000, null)))));
    }

    @Test
    void cambiarElTipoDeIntervaloValidaContraLosValoresQueQuedan() throws Exception {
        MaintenancePlan plan = stored(plan(IntervalType.KM, 10000, null));

        // Pasar a "both" sin mandar días deja el plan incoherente: se rechaza y no se toca.
        assertThrows(MaintenanceValidationException.class, () -> service.update(plan.getId().toString(),
                new UpdateMaintenancePlanRequest(null, null, "both", null, null, null)));
        assertEquals(IntervalType.KM, plan.getIntervalType());

        MaintenancePlanDto updated = service.update(plan.getId().toString(),
                new UpdateMaintenancePlanRequest(null, null, "both", null, 180, null));
        assertEquals("both", updated.intervalType());
        assertEquals(10000, updated.intervalKm());
        assertEquals(180, updated.intervalDays());
    }

    @Test
    void unTipoDeIntervaloInexistenteEnLaEdicionDevuelve422() throws Exception {
        MaintenancePlan plan = stored(plan(IntervalType.KM, 10000, null));

        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.update(plan.getId().toString(),
                        new UpdateMaintenancePlanRequest(null, null, "semanal", null, null, null)));

        assertEquals(List.of("intervalType"), fields(ex));
        assertEquals(IntervalType.KM, plan.getIntervalType());
    }

    @Test
    void unPlanDesactivadoSoloSePuedeReactivar() throws Exception {
        MaintenancePlan plan = plan(IntervalType.KM, 10000, null);
        plan.setActive(false);
        stored(plan);

        MaintenanceConflictException ex = assertThrows(MaintenanceConflictException.class,
                () -> service.update(plan.getId().toString(),
                        new UpdateMaintenancePlanRequest("Otro nombre", null, null, null, null, true)));
        assertEquals("PLAN_INACTIVE", ex.getErrorCode());

        MaintenancePlanDto reactivated = service.update(plan.getId().toString(),
                new UpdateMaintenancePlanRequest(null, null, null, null, null, true));
        assertTrue(reactivated.active());
    }

    @Test
    void editarElNombreLoValidaYLoRecorta() throws Exception {
        MaintenancePlan plan = stored(plan(IntervalType.KM, 10000, null));

        assertThrows(MaintenanceValidationException.class, () -> service.update(plan.getId().toString(),
                new UpdateMaintenancePlanRequest(" ", null, null, null, null, null)));

        MaintenancePlanDto updated = service.update(plan.getId().toString(),
                new UpdateMaintenancePlanRequest(" Aceite y filtro ", "lubricación", null, null, null, false));
        assertEquals("Aceite y filtro", updated.name());
        assertEquals("lubricación", updated.category());
        assertFalse(updated.active());
    }

    @Test
    void unPlanConAsignacionesNoSeBorra() throws Exception {
        MaintenancePlan plan = stored(plan(IntervalType.KM, 10000, null));
        when(assignmentRepository.existsByMaintenancePlan_Id(plan.getId())).thenReturn(true);

        MaintenanceConflictException ex = assertThrows(MaintenanceConflictException.class,
                () -> service.delete(plan.getId().toString()));

        assertEquals("PLAN_IN_USE", ex.getErrorCode());
        verify(planRepository, never()).delete(any());
    }

    @Test
    void unPlanSinAsignacionesSeBorra() throws Exception {
        MaintenancePlan plan = stored(plan(IntervalType.KM, 10000, null));

        service.delete(plan.getId().toString());

        verify(planRepository).delete(plan);
    }

    @Test
    void unIdInexistenteOMalformadoDevuelve404() {
        assertThrows(MaintenancePlanNotFoundException.class, () -> service.delete(UUID.randomUUID().toString()));
        assertThrows(MaintenancePlanNotFoundException.class, () -> service.delete("no-es-un-uuid"));
    }
}
