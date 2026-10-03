package org.fleetguard.service;

import org.fleetguard.dto.CreateScheduleRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.ScheduleDto;
import org.fleetguard.dto.UpdateScheduleRequest;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.ScheduleSourceType;
import org.fleetguard.entity.ScheduleStatus;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.AssignmentNotFoundException;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenanceValidationException;
import org.fleetguard.exception.ScheduleNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Programar mantenimientos en el calendario (CAM-42/CAM-50/CAM-51): desde un plan asignado,
 * desde un defecto o a mano. La regla central es que un mismo plan o defecto tenga una sola
 * programación activa: volver a programarlo la mueve de fecha en vez de duplicarla. La
 * cancelación con su OT (CAM-77) está en ScheduledMaintenanceServiceTest.
 */
class ScheduleCreationServiceTest {

    private final ScheduledMaintenanceRepository scheduleRepository = mock(ScheduledMaintenanceRepository.class);
    private final VehicleMaintenanceAssignmentRepository assignmentRepository = mock(VehicleMaintenanceAssignmentRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final ScheduledMaintenanceService service = new ScheduledMaintenanceService(scheduleRepository,
            assignmentRepository, defectRepository, vehicleRepository, mock(WorkOrderRepository.class),
            mock(UserRepository.class));

    private Vehicle vehicle;
    private final String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(scheduleRepository.save(any(ScheduledMaintenance.class))).thenAnswer(invocation -> {
            ScheduledMaintenance saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, UUID.randomUUID());
            }
            return saved;
        });
    }

    private VehicleMaintenanceAssignment assignment(boolean active) throws Exception {
        MaintenancePlan plan = new MaintenancePlan("Cambio de aceite", "motor", IntervalType.KM, 10000, null);
        setId(plan, UUID.randomUUID());
        VehicleMaintenanceAssignment assignment = new VehicleMaintenanceAssignment(vehicle.getId(), plan, 40000L, null);
        setId(assignment, UUID.randomUUID());
        assignment.setActive(active);
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        return assignment;
    }

    /** Un defecto real cuelga de una respuesta de una inspección del vehículo. */
    private Defect defect() throws Exception {
        Trip trip = new Trip(vehicle, Instant.now());
        Inspection inspection = new Inspection(trip, vehicle.getId(), "driver-1", "Carlos", InspectionType.PRE_TRIP,
                Instant.now(), 50000.0, null, false);
        InspectionAnswer answer = new InspectionAnswer("ext-luces", CheckOutcome.DEFECT, null);
        inspection.addAnswer(answer);
        Defect defect = new Defect(DefectSeverity.NON_BLOCKING, "Foco trasero tenue", null, Instant.now());
        answer.attachDefect(defect);
        setId(defect, UUID.randomUUID());
        when(defectRepository.findById(defect.getId())).thenReturn(Optional.of(defect));
        return defect;
    }

    private static List<String> fields(MaintenanceValidationException ex) {
        return ex.getDetails().stream().map(FieldValidationErrorDetail::field).toList();
    }

    @Test
    void programarUnPlanTomaElVehiculoYElTituloDeLaAsignacion() throws Exception {
        VehicleMaintenanceAssignment assignment = assignment(true);

        ScheduledMaintenanceService.CreateResult result = service.create(new CreateScheduleRequest(
                "assignment", assignment.getId().toString(), null, null, tomorrow, "Llevar al taller"));

        assertTrue(result.created());
        ScheduleDto dto = result.dto();
        assertEquals(vehicle.getId().toString(), dto.vehicleId());
        assertEquals("AB123CD", dto.plate());
        assertEquals("Cambio de aceite", dto.title());
        assertEquals("scheduled", dto.status());
        assertEquals("Llevar al taller", dto.notes());
    }

    @Test
    void volverAProgramarElMismoPlanMueveLaFechaEnVezDeDuplicar() throws Exception {
        VehicleMaintenanceAssignment assignment = assignment(true);
        ScheduledMaintenance active = new ScheduledMaintenance(vehicle.getId(), ScheduleSourceType.ASSIGNMENT,
                assignment.getId(), null, "Cambio de aceite", Instant.now().plus(5, ChronoUnit.DAYS));
        setId(active, UUID.randomUUID());
        when(scheduleRepository.findFirstByAssignmentIdAndStatus(assignment.getId(), ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(active));

        ScheduledMaintenanceService.CreateResult result = service.create(new CreateScheduleRequest(
                "assignment", assignment.getId().toString(), null, null, tomorrow, null));

        // 200 en vez de 201: es la misma programación, con la fecha nueva.
        assertFalse(result.created());
        assertEquals(active.getId().toString(), result.dto().id());
        assertEquals(Instant.parse(tomorrow), active.getScheduledAt());
    }

    @Test
    void noSeProgramaUnaAsignacionDesactivada() throws Exception {
        VehicleMaintenanceAssignment assignment = assignment(false);

        MaintenanceConflictException ex = assertThrows(MaintenanceConflictException.class,
                () -> service.create(new CreateScheduleRequest("assignment", assignment.getId().toString(),
                        null, null, tomorrow, null)));

        assertEquals("ASSIGNMENT_INACTIVE", ex.getErrorCode());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void programarUnDefectoUsaElVehiculoDeLaInspeccionQueLoEncontro() throws Exception {
        Defect defect = defect();

        ScheduleDto dto = service.create(new CreateScheduleRequest("defect", defect.getId().toString(),
                null, null, tomorrow, null)).dto();

        assertEquals(vehicle.getId().toString(), dto.vehicleId());
        assertEquals("Foco trasero tenue", dto.title());
        assertEquals(defect.getId().toString(), dto.defectId());
    }

    @Test
    void noSeProgramaUnDefectoYaResuelto() throws Exception {
        Defect defect = defect();
        defect.resolve();

        assertEquals("DEFECT_RESOLVED", assertThrows(MaintenanceConflictException.class,
                () -> service.create(new CreateScheduleRequest("defect", defect.getId().toString(),
                        null, null, tomorrow, null))).getErrorCode());
    }

    @Test
    void unaProgramacionManualNecesitaVehiculoYTitulo() {
        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateScheduleRequest("manual", null, null, " ", tomorrow, null)));

        assertEquals(List.of("vehicleId", "title"), fields(ex));
    }

    @Test
    void unaProgramacionManualNuncaDeduplica() {
        ScheduleDto dto = service.create(new CreateScheduleRequest("manual", null, vehicle.getId().toString(),
                "  Revisión eléctrica ", tomorrow, null)).dto();

        assertEquals("Revisión eléctrica", dto.title());
        verify(scheduleRepository, never()).findFirstByAssignmentIdAndStatus(any(), any());
        verify(scheduleRepository, never()).findFirstByDefectIdAndStatus(any(), any());
    }

    @Test
    void noSeProgramaEnElPasadoNiConFechaInvalida() {
        String yesterday = Instant.now().minus(1, ChronoUnit.DAYS).toString();

        MaintenanceValidationException past = assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateScheduleRequest("manual", null, vehicle.getId().toString(),
                        "Frenos", yesterday, null)));
        assertEquals("PAST_DATE", past.getDetails().get(0).message());

        assertEquals(List.of("scheduledAt"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateScheduleRequest("manual", null, vehicle.getId().toString(),
                        "Frenos", "mañana", null)))));
    }

    @Test
    void sePuedeProgramarConSoloLaFecha() {
        String nextWeek = java.time.LocalDate.now().plusDays(7).toString();

        ScheduleDto dto = service.create(new CreateScheduleRequest("manual", null, vehicle.getId().toString(),
                "Frenos", nextWeek, null)).dto();

        // Solo fecha (YYYY-MM-DD) se toma como el inicio de ese día en UTC.
        assertEquals(nextWeek + "T00:00:00Z", dto.scheduledAt());
    }

    @Test
    void unOrigenInvalidoOSinIdDevuelve422() {
        // Un origen que no existe tampoco es "manual", así que además se pide el sourceId.
        assertEquals(List.of("sourceType", "sourceId"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateScheduleRequest("calendario", null, null, null, tomorrow, null)))));
        assertEquals(List.of("sourceId"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.create(new CreateScheduleRequest("defect", null, null, null, tomorrow, null)))));
    }

    @Test
    void origenesInexistentesOMalFormadosDevuelven404() {
        assertThrows(AssignmentNotFoundException.class, () -> service.create(new CreateScheduleRequest(
                "assignment", "no-es-un-uuid", null, null, tomorrow, null)));
        assertThrows(VehicleNotFoundException.class, () -> service.create(new CreateScheduleRequest(
                "manual", null, UUID.randomUUID().toString(), "Frenos", tomorrow, null)));
        assertThrows(ScheduleNotFoundException.class, () -> service.update("no-es-un-uuid",
                new UpdateScheduleRequest(null, "done", null)));
    }

    @Test
    void elCalendarioListaLaSemanaPedidaPorVehiculo() throws Exception {
        ScheduledMaintenance s = new ScheduledMaintenance(vehicle.getId(), ScheduleSourceType.MANUAL, null, null,
                "Frenos", Instant.parse("2026-10-06T13:00:00Z"));
        setId(s, UUID.randomUUID());
        Instant from = Instant.parse("2026-10-05T00:00:00Z");
        Instant to = Instant.parse("2026-10-11T23:59:59Z");
        when(scheduleRepository.findByVehicleIdAndScheduledAtBetweenAndStatusOrderByScheduledAtAsc(
                vehicle.getId(), from, to, ScheduleStatus.SCHEDULED)).thenReturn(List.of(s));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));

        List<ScheduleDto> week = service.listByRange(from.toString(), to.toString(), null, vehicle.getId().toString());

        assertEquals(1, week.size());
        assertEquals("AB123CD", week.get(0).plate());
        // Un vehicleId mal formado en el filtro devuelve lista vacía, no 500.
        assertEquals(0, service.listByRange(from.toString(), to.toString(), null, "no-es-un-uuid").size());
    }

    @Test
    void elCalendarioValidaElRangoYElEstado() {
        assertEquals(List.of("from", "to"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.listByRange(null, "ayer", null, null))));
        assertEquals(List.of("status"), fields(assertThrows(MaintenanceValidationException.class,
                () -> service.listByRange("2026-10-05", "2026-10-11", "pendiente", null))));
    }

    @Test
    void registrarElMantenimientoDeUnPlanCierraSuProgramacionActiva() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        ScheduledMaintenance active = new ScheduledMaintenance(vehicle.getId(), ScheduleSourceType.ASSIGNMENT,
                assignmentId, null, "Cambio de aceite", Instant.now().plus(2, ChronoUnit.DAYS));
        when(scheduleRepository.findFirstByAssignmentIdAndStatus(assignmentId, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(active));

        service.closeActiveScheduleForAssignment(assignmentId);

        assertEquals(ScheduleStatus.DONE, active.getStatus());
        verify(scheduleRepository).save(active);
    }
}
