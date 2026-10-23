package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.User;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Avisos al técnico sobre sus OTs (CAM-99: te asignaron una; CAM-100: te la sacaron o se canceló).
 * Corren después del commit del cambio (AFTER_COMMIT, el default): si el cambio se deshace, no se
 * avisa nada. Cada aviso va por NotificationDispatcher, en su propia transacción y sin poder romper
 * el pedido que lo originó. El link es "/": "Mis órdenes" del técnico no vive en la URL.
 */
@Component
public class WorkOrderNotificationListener {

    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter PLANNED_FORMAT = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");
    private static final String LINK = "/";

    private final WorkOrderRepository workOrderRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final ScheduledMaintenanceRepository scheduleRepository;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public WorkOrderNotificationListener(WorkOrderRepository workOrderRepository, VehicleRepository vehicleRepository,
                                         UserRepository userRepository, ScheduledMaintenanceRepository scheduleRepository,
                                         NotificationService notificationService, NotificationDispatcher dispatcher) {
        this.workOrderRepository = workOrderRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onTechnicianChanged(WorkOrderTechnicianChangedEvent event) {
        UUID newTechnicianId = event.newTechnicianId();
        UUID previousTechnicianId = event.previousTechnicianId();
        if (newTechnicianId != null) {
            dispatcher.dispatch("OT " + event.workOrderId() + " asignada", () ->
                    workOrderRepository.findById(event.workOrderId()).ifPresent(workOrder ->
                            notificationService.notify(newTechnicianId, NotificationType.WORK_ORDER_ASSIGNED,
                                    "Te asignaron una orden de trabajo",
                                    subject(workOrder) + plannedFor(workOrder) + ".", LINK)));
        }
        if (previousTechnicianId != null) {
            dispatcher.dispatch("OT " + event.workOrderId() + " desasignada", () ->
                    workOrderRepository.findById(event.workOrderId()).ifPresent(workOrder ->
                            notificationService.notify(previousTechnicianId, NotificationType.WORK_ORDER_UNASSIGNED,
                                    "Ya no tenés a cargo una orden de trabajo",
                                    subject(workOrder) + reassignedTo(newTechnicianId) + ".", LINK)));
        }
    }

    @TransactionalEventListener
    public void onCancelled(WorkOrderCancelledEvent event) {
        dispatcher.dispatch("OT " + event.workOrderId() + " cancelada", () ->
                workOrderRepository.findById(event.workOrderId()).ifPresent(workOrder ->
                        notificationService.notify(event.technicianId(), NotificationType.WORK_ORDER_CANCELLED,
                                "Se canceló una orden de trabajo",
                                subject(workOrder) + ". Ya no tenés que hacerla.", LINK)));
    }

    /** "Cambio de pastillas (AB123CD)". */
    private String subject(WorkOrder workOrder) {
        String plate = vehicleRepository.findById(workOrder.getVehicleId()).map(Vehicle::getPlate).orElse(null);
        return plate == null ? workOrder.getTitle() : workOrder.getTitle() + " (" + plate + ")";
    }

    /** ", planificada para el 25/10 a las 09:00", si la OT viene de una programación del calendario. */
    private String plannedFor(WorkOrder workOrder) {
        if (workOrder.getScheduledMaintenanceId() == null) {
            return "";
        }
        return scheduleRepository.findById(workOrder.getScheduledMaintenanceId())
                .map(ScheduledMaintenance::getScheduledAt)
                .map(at -> ", planificada para el " + PLANNED_FORMAT.format(at.atZone(FLEET_ZONE)))
                .orElse("");
    }

    private String reassignedTo(UUID newTechnicianId) {
        if (newTechnicianId == null) {
            return ": te la desasignaron";
        }
        return userRepository.findById(newTechnicianId)
                .map(User::getUsername)
                .map(username -> ": ahora está a cargo de " + username)
                .orElse(": ahora está a cargo de otro técnico");
    }
}
