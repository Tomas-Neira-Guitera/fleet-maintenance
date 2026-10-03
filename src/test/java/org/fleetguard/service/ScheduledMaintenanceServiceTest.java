package org.fleetguard.service;

import org.fleetguard.dto.ScheduleDto;
import org.fleetguard.dto.UpdateScheduleRequest;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.ScheduleSourceType;
import org.fleetguard.entity.ScheduleStatus;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.User;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenanceValidationException;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// CAM-77: cancelar una programación cancela su OT abierta.
class ScheduledMaintenanceServiceTest {

    private final ScheduledMaintenanceRepository scheduleRepository = mock(ScheduledMaintenanceRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ScheduledMaintenanceService service = new ScheduledMaintenanceService(scheduleRepository,
            mock(VehicleMaintenanceAssignmentRepository.class), mock(DefectRepository.class), vehicleRepository,
            workOrderRepository, userRepository);

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID technicianId = UUID.randomUUID();
    private final List<WorkOrder> workOrders = new ArrayList<>();
    private ScheduledMaintenance schedule;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        schedule = new ScheduledMaintenance(vehicleId, ScheduleSourceType.MANUAL, null, null, "Revisión de frenos",
                Instant.now().plus(2, ChronoUnit.DAYS));
        setId(schedule, UUID.randomUUID());
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        User technician = new User("tecnico", "hash", Role.TECNICO);
        setId(technician, technicianId);
        when(userRepository.findById(technicianId)).thenReturn(Optional.of(technician));

        // Fake del repositorio: filtra de verdad por programación y estado.
        when(workOrderRepository.findByScheduledMaintenanceIdAndStatusIn(eq(schedule.getId()), any()))
                .thenAnswer(inv -> {
                    Collection<WorkOrderStatus> statuses = inv.getArgument(1);
                    return workOrders.stream()
                            .filter(w -> schedule.getId().equals(w.getScheduledMaintenanceId()))
                            .filter(w -> statuses.contains(w.getStatus()))
                            .toList();
                });
    }

    private WorkOrder workOrderFor(ScheduledMaintenance s) {
        WorkOrder workOrder = new WorkOrder(vehicleId, WorkOrderSourceType.SCHEDULED_MAINTENANCE, s.getId(), null, null,
                s.getTitle(), null, WorkOrderExecutionType.INTERNO, null, null, technicianId);
        workOrders.add(workOrder);
        return workOrder;
    }

    private UpdateScheduleRequest cancel(Boolean cancelWorkOrder) {
        return new UpdateScheduleRequest(null, "cancelled", cancelWorkOrder);
    }

    @Test
    void cancellingTheScheduleCancelsItsAssignedWorkOrder() {
        WorkOrder workOrder = workOrderFor(schedule);

        service.update(schedule.getId().toString(), cancel(null));

        assertEquals(ScheduleStatus.CANCELLED, schedule.getStatus());
        assertEquals(WorkOrderStatus.CANCELADA, workOrder.getStatus());
        verify(workOrderRepository).save(workOrder);
    }

    @Test
    void workOrderInProgressRequiresConfirmation() {
        WorkOrder workOrder = workOrderFor(schedule);
        workOrder.start();

        MaintenanceConflictException ex = assertThrows(MaintenanceConflictException.class,
                () -> service.update(schedule.getId().toString(), cancel(null)));

        assertEquals("WORK_ORDER_IN_PROGRESS", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("tecnico"));
        assertEquals(ScheduleStatus.SCHEDULED, schedule.getStatus());
        assertEquals(WorkOrderStatus.EN_PROCESO, workOrder.getStatus());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void confirmedCancellationCancelsTheWorkOrderInProgress() {
        WorkOrder workOrder = workOrderFor(schedule);
        workOrder.start();

        service.update(schedule.getId().toString(), cancel(true));

        assertEquals(ScheduleStatus.CANCELLED, schedule.getStatus());
        assertEquals(WorkOrderStatus.CANCELADA, workOrder.getStatus());
    }

    @Test
    void finalizedWorkOrdersAreNotTouched() {
        WorkOrder workOrder = workOrderFor(schedule);
        workOrder.start();
        workOrder.finalizeOrder("Pastillas cambiadas");

        service.update(schedule.getId().toString(), cancel(null));

        assertEquals(ScheduleStatus.CANCELLED, schedule.getStatus());
        assertEquals(WorkOrderStatus.FINALIZADA, workOrder.getStatus());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void cancellingAScheduleWithoutWorkOrderStillWorks() {
        service.update(schedule.getId().toString(), cancel(null));

        assertEquals(ScheduleStatus.CANCELLED, schedule.getStatus());
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void manualScheduleTitleAndNotesCanBeEdited() {
        schedule.setNotes("Nota vieja");

        ScheduleDto dto = service.update(schedule.getId().toString(),
                new UpdateScheduleRequest(null, null, null, "  Revisión de luces ", ""));

        assertEquals("Revisión de luces", dto.title());
        assertNull(dto.notes());
    }

    @Test
    void titleOfAScheduleFromAPlanCannotBeEdited() throws Exception {
        ScheduledMaintenance fromPlan = new ScheduledMaintenance(vehicleId, ScheduleSourceType.ASSIGNMENT, UUID.randomUUID(),
                null, "Cambio de aceite", Instant.now().plus(1, ChronoUnit.DAYS));
        setId(fromPlan, UUID.randomUUID());
        when(scheduleRepository.findById(fromPlan.getId())).thenReturn(Optional.of(fromPlan));

        MaintenanceValidationException ex = assertThrows(MaintenanceValidationException.class,
                () -> service.update(fromPlan.getId().toString(), new UpdateScheduleRequest(null, null, null, "Otro", null)));

        assertEquals("title", ex.getDetails().get(0).field());
        assertEquals("Cambio de aceite", fromPlan.getTitle());
    }

    @Test
    void editedTitleLongerThanThirtyCharactersIsRejected() {
        assertThrows(MaintenanceValidationException.class, () -> service.update(schedule.getId().toString(),
                new UpdateScheduleRequest(null, null, null, "Revisión completa del sistema eléctrico", null)));
    }

    @Test
    void scheduleShowsItsOpenWorkOrder() throws Exception {
        WorkOrder workOrder = workOrderFor(schedule);
        setId(workOrder, UUID.randomUUID());
        workOrder.start();
        when(workOrderRepository.findFirstByScheduledMaintenanceIdAndStatusInOrderByCreatedAtAsc(eq(schedule.getId()), any()))
                .thenReturn(Optional.of(workOrder));

        ScheduleDto dto = service.update(schedule.getId().toString(), new UpdateScheduleRequest(null, null, null, null, "Ok"));

        assertEquals("en_proceso", dto.workOrder().status());
        assertEquals("tecnico", dto.workOrder().responsible());
    }
}
