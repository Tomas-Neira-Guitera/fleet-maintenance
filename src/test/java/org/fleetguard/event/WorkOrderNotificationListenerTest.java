package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.User;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-99/CAM-100 -- qué aviso recibe cada técnico cuando cambia una OT suya. */
class WorkOrderNotificationListenerTest {

    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ScheduledMaintenanceRepository scheduleRepository = mock(ScheduledMaintenanceRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final WorkOrderNotificationListener listener = new WorkOrderNotificationListener(workOrderRepository,
            vehicleRepository, userRepository, scheduleRepository, notificationService,
            new NotificationDispatcher(mock(PlatformTransactionManager.class)));

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID previousId = UUID.randomUUID();
    private final UUID newId = UUID.randomUUID();
    private WorkOrder workOrder;

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
        User newTechnician = new User("juan.perez", "hash", Role.TECNICO);
        setId(newTechnician, newId);
        when(userRepository.findById(newId)).thenReturn(Optional.of(newTechnician));
        workOrder = manualWorkOrder(null);
    }

    private WorkOrder manualWorkOrder(UUID scheduledMaintenanceId) throws Exception {
        WorkOrder wo = new WorkOrder(vehicleId, WorkOrderSourceType.MANUAL, scheduledMaintenanceId, null, null,
                "Cambio de pastillas", null, WorkOrderExecutionType.INTERNO, null, null, null);
        setId(wo, UUID.randomUUID());
        when(workOrderRepository.findById(wo.getId())).thenReturn(Optional.of(wo));
        return wo;
    }

    @Test
    void alTecnicoNuevoLeAvisaQueSeLaAsignaron() {
        listener.onTechnicianChanged(new WorkOrderTechnicianChangedEvent(workOrder.getId(), null, newId));

        verify(notificationService).notify(newId, NotificationType.WORK_ORDER_ASSIGNED,
                "Te asignaron una orden de trabajo", "Cambio de pastillas (AB123CD).", "/");
        verify(notificationService, never()).notify(eq(previousId), any(), any(), any(), any());
    }

    @Test
    void siVieneDelCalendarioAgregaCuandoEstaPlanificada() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        ScheduledMaintenance schedule = mock(ScheduledMaintenance.class);
        // 12:00 UTC son las 09:00 en Argentina.
        when(schedule.getScheduledAt()).thenReturn(Instant.parse("2026-10-25T12:00:00Z"));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        WorkOrder planned = manualWorkOrder(scheduleId);

        listener.onTechnicianChanged(new WorkOrderTechnicianChangedEvent(planned.getId(), null, newId));

        verify(notificationService).notify(newId, NotificationType.WORK_ORDER_ASSIGNED,
                "Te asignaron una orden de trabajo",
                "Cambio de pastillas (AB123CD), planificada para el 25/10 a las 09:00.", "/");
    }

    @Test
    void alReasignarLeAvisaAlNuevoYAlAnteriorConQuienLaTieneAhora() {
        listener.onTechnicianChanged(new WorkOrderTechnicianChangedEvent(workOrder.getId(), previousId, newId));

        verify(notificationService).notify(eq(newId), eq(NotificationType.WORK_ORDER_ASSIGNED), anyString(), anyString(), eq("/"));
        verify(notificationService).notify(previousId, NotificationType.WORK_ORDER_UNASSIGNED,
                "Ya no tenés a cargo una orden de trabajo",
                "Cambio de pastillas (AB123CD): ahora está a cargo de juan.perez.", "/");
    }

    @Test
    void alDesasignarSoloLeAvisaAlAnterior() {
        listener.onTechnicianChanged(new WorkOrderTechnicianChangedEvent(workOrder.getId(), previousId, null));

        verify(notificationService).notify(previousId, NotificationType.WORK_ORDER_UNASSIGNED,
                "Ya no tenés a cargo una orden de trabajo", "Cambio de pastillas (AB123CD): te la desasignaron.", "/");
        verify(notificationService, never()).notify(any(), eq(NotificationType.WORK_ORDER_ASSIGNED), any(), any(), any());
    }

    @Test
    void siFallaElAvisoAlNuevoIgualLeAvisaAlAnterior() {
        doThrow(new IllegalStateException("base caída")).when(notificationService)
                .notify(eq(newId), any(), anyString(), anyString(), any());

        listener.onTechnicianChanged(new WorkOrderTechnicianChangedEvent(workOrder.getId(), previousId, newId));

        verify(notificationService).notify(eq(previousId), eq(NotificationType.WORK_ORDER_UNASSIGNED),
                anyString(), anyString(), any());
    }

    @Test
    void alCancelarLeAvisaAlTecnicoQueLaTenia() {
        listener.onCancelled(new WorkOrderCancelledEvent(workOrder.getId(), previousId));

        verify(notificationService).notify(previousId, NotificationType.WORK_ORDER_CANCELLED,
                "Se canceló una orden de trabajo", "Cambio de pastillas (AB123CD). Ya no tenés que hacerla.", "/");
    }

    @Test
    void siLaOtYaNoExisteNoAvisaNada() {
        UUID missing = UUID.randomUUID();
        when(workOrderRepository.findById(missing)).thenReturn(Optional.empty());

        listener.onTechnicianChanged(new WorkOrderTechnicianChangedEvent(missing, previousId, newId));
        listener.onCancelled(new WorkOrderCancelledEvent(missing, previousId));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
