package org.fleetguard.service;

import org.fleetguard.dto.CompletionResultDto;
import org.fleetguard.dto.CreateCompletionRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenanceValidationException;
import org.fleetguard.mapper.MaintenanceCompletionMapper;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registrar que un mantenimiento se hizo (CAM-16/CAM-40). Es lo que mueve el próximo
 * vencimiento hacia adelante y cierra la programación del calendario (CAM-42), así que un
 * registro con fecha o kilometraje inválidos dejaría mal toda la vista de flota.
 */
class MaintenanceCompletionServiceTest {

    private final MaintenanceCompletionRepository completionRepository = mock(MaintenanceCompletionRepository.class);
    private final VehicleMaintenanceAssignmentService assignmentService = mock(VehicleMaintenanceAssignmentService.class);
    private final ScheduledMaintenanceService scheduledMaintenanceService = mock(ScheduledMaintenanceService.class);
    private final MaintenanceCompletionService service = new MaintenanceCompletionService(completionRepository,
            assignmentService, new MaintenanceCompletionMapper(), scheduledMaintenanceService);

    private Vehicle vehicle;
    private VehicleMaintenanceAssignment assignment;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        vehicle.setOdometerKm(52000);
        when(assignmentService.findVehicle(vehicle.getId().toString())).thenReturn(vehicle);
        assignmentWith(IntervalType.BOTH, 10000, 180);
        when(completionRepository.save(any(MaintenanceCompletion.class))).thenAnswer(invocation -> {
            MaintenanceCompletion saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });
    }

    private void assignmentWith(IntervalType type, Integer km, Integer days) throws Exception {
        MaintenancePlan plan = new MaintenancePlan("Cambio de aceite", "motor", type, km, days);
        setId(plan, UUID.randomUUID());
        assignment = new VehicleMaintenanceAssignment(vehicle.getId(), plan, 40000L, LocalDate.now().minusDays(200));
        setId(assignment, UUID.randomUUID());
        assignment.recalculateNextDue();
        when(assignmentService.findAssignment(vehicle.getId(), assignment.getId().toString())).thenReturn(assignment);
    }

    private CompletionResultDto complete(String date, Long km) {
        return service.create(vehicle.getId().toString(), assignment.getId().toString(),
                new CreateCompletionRequest(date, km, null, "Hecho en el taller"));
    }

    private static List<String> messages(MaintenanceValidationException ex) {
        return ex.getDetails().stream().map(FieldValidationErrorDetail::message).toList();
    }

    @Test
    void registrarUnMantenimientoMueveElProximoVencimientoYCierraLaProgramacion() {
        String today = LocalDate.now().toString();

        CompletionResultDto result = complete(today, 51000L);

        assertEquals(51000L, assignment.getLastDoneKm());
        assertEquals(LocalDate.now(), assignment.getLastDoneDate());
        assertEquals(61000L, result.updatedAssignment().nextDueKm());
        assertEquals(LocalDate.now().plusDays(180).toString(), result.updatedAssignment().nextDueDate());
        // Antes estaba vencido (venció a los 50.000 km); con el registro vuelve a estar al día.
        assertEquals("al_dia", result.updatedAssignment().status());
        verify(scheduledMaintenanceService).closeActiveScheduleForAssignment(assignment.getId());
    }

    @Test
    void enUnPlanPorTiempoElKilometrajeEsOpcionalYNoPisaElUltimoRegistrado() throws Exception {
        assignmentWith(IntervalType.TIME, null, 365);

        complete(LocalDate.now().toString(), null);

        assertEquals(40000L, assignment.getLastDoneKm());
        assertEquals(LocalDate.now().plusDays(365), assignment.getNextDueDate());
    }

    @Test
    void enUnPlanPorKilometrajeElKilometrajeEsObligatorio() throws Exception {
        assignmentWith(IntervalType.KM, 10000, null);

        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> complete(LocalDate.now().toString(), null));

        assertEquals("completedKm", ex.getDetails().get(0).field());
        verify(completionRepository, never()).save(any());
    }

    @Test
    void noSeRegistraUnMantenimientoConFechaFutura() {
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> complete(LocalDate.now().plusDays(1).toString(), 51000L));

        assertEquals(List.of("FUTURE_DATE"), messages(ex));
    }

    @Test
    void noSeRegistraUnMantenimientoAnteriorAlUltimoRegistrado() {
        MaintenanceCompletion latest = new MaintenanceCompletion(assignment, LocalDate.now().minusDays(10), 50000L, null, null);
        when(completionRepository.findFirstByAssignment_IdOrderByCompletedAtDesc(assignment.getId()))
                .thenReturn(Optional.of(latest));

        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> complete(LocalDate.now().minusDays(20).toString(), 49000L));

        // Si se aceptara, el "último hecho" retrocedería y el vencimiento quedaría mal calculado.
        assertEquals(List.of("OUT_OF_ORDER_COMPLETION"), messages(ex));
        assertEquals(40000L, assignment.getLastDoneKm());
    }

    @Test
    void laFechaEsObligatoriaYConFormatoISO() {
        assertEquals("completedAt", assertThrows(MaintenanceValidationException.class,
                () -> complete(null, 51000L)).getDetails().get(0).field());
        assertEquals("completedAt", assertThrows(MaintenanceValidationException.class,
                () -> complete("03/10/2026", 51000L)).getDetails().get(0).field());
    }

    @Test
    void noSeRegistraSobreUnaAsignacionDesactivada() {
        assignment.setActive(false);

        MaintenanceConflictException ex = assertThrows(MaintenanceConflictException.class,
                () -> complete(LocalDate.now().toString(), 51000L));

        assertEquals("ASSIGNMENT_INACTIVE", ex.getErrorCode());
        verify(scheduledMaintenanceService, never()).closeActiveScheduleForAssignment(any());
    }

}
