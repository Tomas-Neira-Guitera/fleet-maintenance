package org.example.service;

import org.example.dto.CreateWorkOrderRequest;
import org.example.dto.UpdateWorkOrderRequest;
import org.example.dto.WorkOrderDto;
import org.example.entity.Defect;
import org.example.entity.Role;
import org.example.entity.ScheduledMaintenance;
import org.example.entity.User;
import org.example.entity.Vehicle;
import org.example.entity.WorkOrder;
import org.example.entity.WorkOrderExecutionType;
import org.example.entity.WorkOrderSourceType;
import org.example.exception.WorkOrderValidationException;
import org.example.mapper.DefectMapper;
import org.example.mapper.WorkOrderMapper;
import org.example.repository.DefectRepository;
import org.example.repository.ScheduledMaintenanceRepository;
import org.example.repository.UserRepository;
import org.example.repository.VehicleRepository;
import org.example.repository.WorkOrderExpenseRepository;
import org.example.repository.WorkOrderPhotoRepository;
import org.example.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-60 -- vínculo técnico ↔ orden de trabajo. */
class WorkOrderServiceTest {

    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final WorkOrderExpenseRepository expenseRepository = mock(WorkOrderExpenseRepository.class);
    private final WorkOrderPhotoRepository photoRepository = mock(WorkOrderPhotoRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final ScheduledMaintenanceRepository scheduleRepository = mock(ScheduledMaintenanceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WorkOrderService service = new WorkOrderService(workOrderRepository, expenseRepository, photoRepository,
            vehicleRepository, defectRepository, scheduleRepository, mock(ScheduledMaintenanceService.class),
            mock(MaintenanceCompletionService.class), userRepository, new WorkOrderMapper(), new DefectMapper());

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID technicianId = UUID.randomUUID();
    private final UUID choferId = UUID.randomUUID();

    /** El id es @GeneratedValue; en el test se setea por reflexión (mismo patrón que VehicleServiceTest). */
    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, vehicleId);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));

        User technician = new User("tecnico", "hash", Role.TECNICO);
        setId(technician, technicianId);
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));

        User chofer = new User("chofer", "hash", Role.CHOFER);
        setId(chofer, choferId);
        when(userRepository.findById(choferId)).thenReturn(Optional.of(chofer));

        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder w = invocation.getArgument(0);
            if (w.getId() == null) {
                setId(w, UUID.randomUUID());
            }
            return w;
        });
    }

    private CreateWorkOrderRequest manualRequest(String executionType, String externalProvider, String technician) {
        return new CreateWorkOrderRequest("manual", null, vehicleId.toString(), "Cambio de pastillas", null,
                executionType, externalProvider, null, technician);
    }

    private UpdateWorkOrderRequest updateRequest(String technician, String executionType, String externalProvider) {
        return new UpdateWorkOrderRequest(null, null, null, null, technician, executionType, externalProvider, null);
    }

    private WorkOrder existingInternalWithTechnician() throws Exception {
        WorkOrder workOrder = new WorkOrder(vehicleId, WorkOrderSourceType.MANUAL, null, null, null, "Frenos", null,
                WorkOrderExecutionType.INTERNO, null, null, technicianId);
        setId(workOrder, UUID.randomUUID());
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Optional.of(workOrder));
        return workOrder;
    }

    @Test
    void crearOtInternaConTecnicoDevuelveIdYNombreDelTecnico() {
        WorkOrderDto result = service.create(manualRequest("interno", null, technicianId.toString())).dto();

        assertEquals(technicianId.toString(), result.technicianId());
        assertEquals("tecnico", result.technicianUsername());
    }

    @Test
    void crearOtSinTecnicoLoDejaVacio() {
        WorkOrderDto result = service.create(manualRequest("interno", null, null)).dto();

        assertNull(result.technicianId());
        assertNull(result.technicianUsername());
    }

    @Test
    void crearOtConUsuarioQueNoEsTecnicoDevuelve422() {
        WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class,
                () -> service.create(manualRequest("interno", null, choferId.toString())));

        assertEquals("technicianId", ex.getDetails().get(0).field());
    }

    @Test
    void crearOtConTecnicoInexistenteOMalformadoDevuelve422() {
        assertThrows(WorkOrderValidationException.class,
                () -> service.create(manualRequest("interno", null, UUID.randomUUID().toString())));
        assertThrows(WorkOrderValidationException.class,
                () -> service.create(manualRequest("interno", null, "no-es-un-uuid")));
    }

    @Test
    void crearOtExternaConTecnicoDevuelve422() {
        WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class,
                () -> service.create(manualRequest("externo", "Taller Norte", technicianId.toString())));

        assertEquals("technicianId", ex.getDetails().get(0).field());
    }

    @Test
    void actualizarConTecnicoVacioLoDesasigna() throws Exception {
        WorkOrder workOrder = existingInternalWithTechnician();

        WorkOrderDto result = service.update(workOrder.getId().toString(), updateRequest("", null, null));

        assertNull(result.technicianId());
    }

    @Test
    void actualizarSinMandarTecnicoNoLoToca() throws Exception {
        WorkOrder workOrder = existingInternalWithTechnician();

        WorkOrderDto result = service.update(workOrder.getId().toString(), updateRequest(null, null, null));

        assertEquals(technicianId.toString(), result.technicianId());
    }

    @Test
    void pasarUnaOtAExternaDesasignaAlTecnico() throws Exception {
        WorkOrder workOrder = existingInternalWithTechnician();

        WorkOrderDto result = service.update(workOrder.getId().toString(), updateRequest(null, "externo", "Taller Norte"));

        assertEquals("externo", result.executionType());
        assertNull(result.technicianId());
    }

    @Test
    void listarPorTecnicoDevuelveSoloSusOts() throws Exception {
        WorkOrder mine = existingInternalWithTechnician();
        WorkOrder other = new WorkOrder(vehicleId, WorkOrderSourceType.MANUAL, null, null, null, "Luces", null,
                WorkOrderExecutionType.INTERNO, null, null, null);
        setId(other, UUID.randomUUID());
        when(workOrderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(mine, other));

        List<WorkOrderDto> result = service.list(null, null, null, technicianId.toString());

        assertEquals(1, result.size());
        assertEquals(mine.getId().toString(), result.get(0).id());
        assertEquals(0, service.list(null, null, null, "no-es-un-uuid").size());
        assertEquals(1, service.list(null, null, null, technicianId.toString().toUpperCase()).size());
        assertEquals(2, service.list(null, null, null, "").size());
    }

    @Test
    void pasarAExternaYAsignarTecnicoEnElMismoPatchDevuelve422() throws Exception {
        WorkOrder workOrder = existingInternalWithTechnician();

        WorkOrderValidationException ex = assertThrows(WorkOrderValidationException.class,
                () -> service.update(workOrder.getId().toString(),
                        updateRequest(technicianId.toString(), "externo", "Taller Norte")));

        assertEquals("technicianId", ex.getDetails().get(0).field());
    }

    @Test
    void asignarTecnicoAUnaOtExternaDevuelve422() throws Exception {
        WorkOrder workOrder = new WorkOrder(vehicleId, WorkOrderSourceType.MANUAL, null, null, null, "Cubiertas", null,
                WorkOrderExecutionType.EXTERNO, "Taller Norte", "Carlos", null);
        setId(workOrder, UUID.randomUUID());
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Optional.of(workOrder));

        assertThrows(WorkOrderValidationException.class,
                () -> service.update(workOrder.getId().toString(), updateRequest(technicianId.toString(), null, null)));
    }

    @Test
    void pasarUnaOtExternaAInternaLimpiaContactoYProveedor() throws Exception {
        WorkOrder workOrder = new WorkOrder(vehicleId, WorkOrderSourceType.MANUAL, null, null, null, "Cubiertas", null,
                WorkOrderExecutionType.EXTERNO, "Taller Norte", "Carlos", null);
        setId(workOrder, UUID.randomUUID());
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Optional.of(workOrder));

        WorkOrderDto result = service.update(workOrder.getId().toString(),
                updateRequest(technicianId.toString(), "interno", null));

        assertEquals("interno", result.executionType());
        assertNull(result.assignee());
        assertNull(result.externalProvider());
        assertEquals("tecnico", result.technicianUsername());
    }

    // --- Una sola OT abierta por origen (CAM-60) ---

    private ScheduledMaintenance scheduleFor(UUID defectId) {
        ScheduledMaintenance schedule = mock(ScheduledMaintenance.class);
        UUID scheduleId = UUID.randomUUID();
        when(schedule.getId()).thenReturn(scheduleId);
        when(schedule.getVehicleId()).thenReturn(vehicleId);
        when(schedule.getDefectId()).thenReturn(defectId);
        when(schedule.getTitle()).thenReturn("Cambio de aceite");
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        return schedule;
    }

    private CreateWorkOrderRequest fromSchedule(ScheduledMaintenance schedule) {
        return new CreateWorkOrderRequest("scheduled_maintenance", schedule.getId().toString(), null, null, null,
                "interno", null, null, null);
    }

    @Test
    void replanificarConUnaOtAbiertaDevuelveLaExistenteSinCrearOtra() throws Exception {
        ScheduledMaintenance schedule = scheduleFor(null);
        WorkOrder existing = new WorkOrder(vehicleId, WorkOrderSourceType.SCHEDULED_MAINTENANCE, schedule.getId(), null,
                null, "Cambio de aceite", null, WorkOrderExecutionType.INTERNO, null, null, technicianId);
        setId(existing, UUID.randomUUID());
        when(workOrderRepository.findFirstByScheduledMaintenanceIdAndStatusInOrderByCreatedAtAsc(eq(schedule.getId()), any()))
                .thenReturn(Optional.of(existing));

        WorkOrderService.CreateResult result = service.create(fromSchedule(schedule));

        assertFalse(result.created());
        assertEquals(existing.getId().toString(), result.dto().id());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void sinOtAbiertaParaEseOrigenCreaUnaNueva() {
        ScheduledMaintenance schedule = scheduleFor(null);

        WorkOrderService.CreateResult result = service.create(fromSchedule(schedule));

        assertTrue(result.created());
        verify(workOrderRepository).save(any(WorkOrder.class));
    }

    @Test
    void unaProgramacionDeOrigenDefectoSeDeduplicaPorElDefecto() throws Exception {
        UUID defectId = UUID.randomUUID();
        ScheduledMaintenance schedule = scheduleFor(defectId);
        WorkOrder existing = new WorkOrder(vehicleId, WorkOrderSourceType.DEFECT, null, defectId, null,
                "Pérdida de aceite", null, WorkOrderExecutionType.INTERNO, null, null, null);
        setId(existing, UUID.randomUUID());
        when(workOrderRepository.findFirstByDefectIdAndStatusInOrderByCreatedAtAsc(eq(defectId), any()))
                .thenReturn(Optional.of(existing));

        WorkOrderService.CreateResult result = service.create(fromSchedule(schedule));

        assertFalse(result.created());
        assertEquals(existing.getId().toString(), result.dto().id());
        // La OT reusada queda apuntando a la programación vigente, que es la que cierra al finalizar.
        assertEquals(schedule.getId().toString(), result.dto().scheduledMaintenanceId());
    }

    @Test
    void cancelarYReplanificarUnPlanReusaLaOtAbiertaDeEsaAsignacion() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        ScheduledMaintenance newSchedule = scheduleFor(null);
        when(newSchedule.getAssignmentId()).thenReturn(assignmentId);
        WorkOrder existing = new WorkOrder(vehicleId, WorkOrderSourceType.SCHEDULED_MAINTENANCE, UUID.randomUUID(), null,
                assignmentId, "Cambio de aceite", null, WorkOrderExecutionType.INTERNO, null, null, technicianId);
        setId(existing, UUID.randomUUID());
        when(workOrderRepository.findFirstByAssignmentIdAndStatusInOrderByCreatedAtAsc(eq(assignmentId), any()))
                .thenReturn(Optional.of(existing));

        WorkOrderService.CreateResult result = service.create(fromSchedule(newSchedule));

        assertFalse(result.created());
        assertEquals(existing.getId().toString(), result.dto().id());
        assertEquals(newSchedule.getId().toString(), result.dto().scheduledMaintenanceId());
        assertEquals("tecnico", result.dto().technicianUsername());
    }

    @Test
    void crearDirectoDesdeUnDefectoConOtAbiertaDevuelveLaExistente() throws Exception {
        UUID defectId = UUID.randomUUID();
        Defect defect = mock(Defect.class, RETURNS_DEEP_STUBS);
        when(defect.getId()).thenReturn(defectId);
        when(defect.getStatus()).thenReturn("open");
        when(defect.getDescription()).thenReturn("Pérdida de aceite");
        when(defect.getInspectionAnswer().getInspection().getVehicleId()).thenReturn(vehicleId);
        when(defectRepository.findById(defectId)).thenReturn(Optional.of(defect));
        WorkOrder existing = new WorkOrder(vehicleId, WorkOrderSourceType.DEFECT, null, defectId, null,
                "Pérdida de aceite", null, WorkOrderExecutionType.INTERNO, null, null, null);
        setId(existing, UUID.randomUUID());
        when(workOrderRepository.findFirstByDefectIdAndStatusInOrderByCreatedAtAsc(eq(defectId), any()))
                .thenReturn(Optional.of(existing));

        WorkOrderService.CreateResult result = service.create(new CreateWorkOrderRequest("defect", defectId.toString(),
                null, null, null, "interno", null, null, technicianId.toString()));

        assertFalse(result.created());
        assertEquals(existing.getId().toString(), result.dto().id());
        // No se pisa: el técnico que venía en el body no se aplica sobre la OT existente.
        assertNull(result.dto().technicianId());
        verify(workOrderRepository, never()).save(any());
    }
}
