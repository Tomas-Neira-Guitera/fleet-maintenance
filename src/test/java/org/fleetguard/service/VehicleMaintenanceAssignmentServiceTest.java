package org.fleetguard.service;

import org.fleetguard.dto.AssignmentDto;
import org.fleetguard.dto.CreateAssignmentRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.UpdateAssignmentRequest;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.AssignmentNotFoundException;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenanceValidationException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.mapper.MaintenancePlanMapper;
import org.fleetguard.repository.MaintenancePlanRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Asignar un plan a un vehículo (CAM-16/CAM-40): la asignación es la que guarda cuándo se hizo
 * la última vez y calcula cuándo vence la próxima, que es lo que muestran el dashboard y la
 * tabla de flota. Se usa el MaintenancePlanService real (con repositorios mockeados).
 */
class VehicleMaintenanceAssignmentServiceTest {

    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final MaintenancePlanRepository planRepository = mock(MaintenancePlanRepository.class);
    private final VehicleMaintenanceAssignmentService service = new VehicleMaintenanceAssignmentService(
            assignmentRepository, vehicleRepository,
            new MaintenancePlanService(planRepository, assignmentRepository, new MaintenancePlanMapper()));

    private Vehicle vehicle;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        vehicle.setOdometerKm(50000);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(assignmentRepository.save(any(VehicleMaintenanceAssignment.class))).thenAnswer(invocation -> {
            VehicleMaintenanceAssignment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, UUID.randomUUID());
            }
            return saved;
        });
    }

    private MaintenancePlan plan(IntervalType type, Integer km, Integer days) throws Exception {
        MaintenancePlan plan = new MaintenancePlan("Cambio de aceite", "motor", type, km, days);
        setId(plan, UUID.randomUUID());
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        return plan;
    }

    private VehicleMaintenanceAssignment existing(MaintenancePlan plan, Long lastKm, LocalDate lastDate) throws Exception {
        VehicleMaintenanceAssignment assignment = new VehicleMaintenanceAssignment(vehicle.getId(), plan, lastKm, lastDate);
        setId(assignment, UUID.randomUUID());
        assignment.recalculateNextDue();
        when(assignmentRepository.findByIdAndVehicleId(assignment.getId(), vehicle.getId())).thenReturn(Optional.of(assignment));
        return assignment;
    }

    private static List<String> fields(MaintenanceValidationException ex) {
        return ex.getDetails().stream().map(FieldValidationErrorDetail::field).toList();
    }

    @Test
    void asignarConUltimaVezCalculaElProximoVencimiento() throws Exception {
        MaintenancePlan plan = plan(IntervalType.BOTH, 10000, 180);

        AssignmentDto result = service.create(vehicle.getId().toString(),
                new CreateAssignmentRequest(plan.getId().toString(), 45000L, "2026-09-01"));

        assertEquals(55000L, result.nextDueKm());
        assertEquals("2027-02-28", result.nextDueDate());
        // Al vehículo (50.000 km) le faltan 5.000 km: más que el umbral de aviso de 1.000.
        assertEquals("al_dia", result.status());
        assertTrue(result.active());
    }

    @Test
    void asignarSinUltimaVezTomaElEstadoActualDelVehiculoComoPuntoDePartida() throws Exception {
        MaintenancePlan plan = plan(IntervalType.BOTH, 10000, 180);

        AssignmentDto result = service.create(vehicle.getId().toString(),
                new CreateAssignmentRequest(plan.getId().toString(), null, null));

        assertEquals(50000L, result.lastDoneKm());
        assertEquals(LocalDate.now().toString(), result.lastDoneDate());
        assertEquals(60000L, result.nextDueKm());
    }

    @Test
    void unPlanPorTiempoSinUltimaVezNoSiembraKilometraje() throws Exception {
        MaintenancePlan plan = plan(IntervalType.TIME, null, 365);

        AssignmentDto result = service.create(vehicle.getId().toString(),
                new CreateAssignmentRequest(plan.getId().toString(), null, null));

        assertNull(result.lastDoneKm());
        assertNull(result.nextDueKm());
        assertEquals(LocalDate.now().plusDays(365).toString(), result.nextDueDate());
    }

    @Test
    void siSeMandaSoloUnDatoDeUltimaVezSeExigeElQueCorrespondeAlPlan() throws Exception {
        MaintenancePlan plan = plan(IntervalType.KM, 10000, null);

        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.create(vehicle.getId().toString(),
                        new CreateAssignmentRequest(plan.getId().toString(), null, "2026-09-01")));

        assertEquals(List.of("lastDoneKm"), fields(ex));
    }

    @Test
    void unaFechaMalFormadaDevuelve422() throws Exception {
        MaintenancePlan plan = plan(IntervalType.TIME, null, 365);

        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.create(vehicle.getId().toString(),
                        new CreateAssignmentRequest(plan.getId().toString(), null, "01/09/2026")));

        // Solo el error de formato: no se suma además "Obligatorio" por la fecha que no se pudo leer.
        assertEquals(List.of("lastDoneDate"), fields(ex));
    }

    @Test
    void noSeAsignaUnPlanDesactivadoNiSinPlan() throws Exception {
        MaintenancePlan plan = plan(IntervalType.KM, 10000, null);
        plan.setActive(false);

        assertEquals(List.of("maintenancePlanId"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.create(vehicle.getId().toString(),
                        new CreateAssignmentRequest(plan.getId().toString(), 45000L, null)))));
        assertEquals(List.of("maintenancePlanId"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.create(vehicle.getId().toString(), new CreateAssignmentRequest(null, 45000L, null)))));
    }

    @Test
    void noSeAsignaDosVecesElMismoPlanActivo() throws Exception {
        MaintenancePlan plan = plan(IntervalType.KM, 10000, null);
        VehicleMaintenanceAssignment active = existing(plan, 40000L, null);
        when(assignmentRepository.findFirstByVehicleIdAndMaintenancePlan_IdAndActiveTrue(vehicle.getId(), plan.getId()))
                .thenReturn(Optional.of(active));

        MaintenanceConflictException ex = assertThrows(MaintenanceConflictException.class,
                () -> service.create(vehicle.getId().toString(),
                        new CreateAssignmentRequest(plan.getId().toString(), 45000L, null)));

        assertEquals("DUPLICATE_ACTIVE_ASSIGNMENT", ex.getErrorCode());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void volverAAsignarUnPlanQueEstabaDesasignadoReactivaLaMismaAsignacion() throws Exception {
        MaintenancePlan plan = plan(IntervalType.KM, 10000, null);
        VehicleMaintenanceAssignment old = existing(plan, 20000L, null);
        old.setActive(false);
        when(assignmentRepository.findFirstByVehicleIdAndMaintenancePlan_IdAndActiveFalse(vehicle.getId(), plan.getId()))
                .thenReturn(Optional.of(old));

        AssignmentDto result = service.create(vehicle.getId().toString(),
                new CreateAssignmentRequest(plan.getId().toString(), 45000L, null));

        // Misma fila (conserva su historial de completions), activa y con los datos nuevos.
        assertEquals(old.getId().toString(), result.id());
        assertTrue(old.isActive());
        assertEquals(55000L, old.getNextDueKm());
    }

    @Test
    void editarLaUltimaVezRecalculaElVencimiento() throws Exception {
        MaintenancePlan plan = plan(IntervalType.BOTH, 10000, 180);
        VehicleMaintenanceAssignment assignment = existing(plan, 30000L, LocalDate.of(2026, 1, 1));

        AssignmentDto result = service.update(vehicle.getId().toString(), assignment.getId().toString(),
                new UpdateAssignmentRequest(null, 48000L, "2026-08-01"));

        assertEquals(58000L, result.nextDueKm());
        assertEquals("2027-01-28", result.nextDueDate());
    }

    @Test
    void editarConUnaFechaMalFormadaNoTocaLaAsignacion() throws Exception {
        MaintenancePlan plan = plan(IntervalType.TIME, null, 180);
        VehicleMaintenanceAssignment assignment = existing(plan, null, LocalDate.of(2026, 1, 1));

        assertThrows(MaintenanceValidationException.class, () -> service.update(vehicle.getId().toString(),
                assignment.getId().toString(), new UpdateAssignmentRequest(null, null, "ayer")));

        assertEquals(LocalDate.of(2026, 1, 1), assignment.getLastDoneDate());
    }

    @Test
    void unaAsignacionVencidaSeInformaComoVencida() throws Exception {
        MaintenancePlan plan = plan(IntervalType.KM, 10000, null);
        // Última vez a los 30.000 km: vencía a los 40.000 y el vehículo ya tiene 50.000.
        VehicleMaintenanceAssignment overdue = existing(plan, 30000L, null);
        when(assignmentRepository.findByVehicleIdAndActive(vehicle.getId(), true)).thenReturn(List.of(overdue));

        List<AssignmentDto> list = service.list(vehicle.getId().toString(), true);

        assertEquals("vencido", list.get(0).status());
    }

    @Test
    void desasignarEsUnaBajaLogica() throws Exception {
        MaintenancePlan plan = plan(IntervalType.KM, 10000, null);
        VehicleMaintenanceAssignment assignment = existing(plan, 30000L, null);

        service.delete(vehicle.getId().toString(), assignment.getId().toString());

        assertFalse(assignment.isActive());
        verify(assignmentRepository).save(assignment);
        verify(assignmentRepository, never()).delete(any());
    }

    @Test
    void idsInexistentesOMalFormadosDevuelven404() {
        assertThrows(VehicleNotFoundException.class, () -> service.list("no-es-un-uuid", true));
        assertThrows(VehicleNotFoundException.class, () -> service.list(UUID.randomUUID().toString(), true));
        assertThrows(AssignmentNotFoundException.class,
                () -> service.delete(vehicle.getId().toString(), "no-es-un-uuid"));
        assertThrows(AssignmentNotFoundException.class,
                () -> service.delete(vehicle.getId().toString(), UUID.randomUUID().toString()));
    }
}
