package org.fleetguard.event;

import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * CAM-141: al chofer que reportó un defecto, cuando se finaliza su OT. Después del commit y por
 * NotificationDispatcher, así que una falla no le llega a quien finalizó la OT.
 * <ul>
 *   <li>Si era bloqueante y el vehículo volvió a estar disponible, no se avisa: el chofer ya recibe
 *       VEHICLE_AVAILABLE.</li>
 *   <li>Si el driverId no es un UUID (los de antes de CAM-85) o el usuario ya no existe, tampoco.</li>
 * </ul>
 * El link es "/": el aviso no tiene a dónde llevar al chofer, y tocarlo solo lo marca leído.
 */
@Component
public class DefectResolvedNotificationListener {

    static final String LINK = "/";

    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public DefectResolvedNotificationListener(DefectRepository defectRepository, WorkOrderRepository workOrderRepository,
                                              VehicleRepository vehicleRepository, UserRepository userRepository,
                                              NotificationService notificationService, NotificationDispatcher dispatcher) {
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onDefectResolved(DefectResolvedEvent event) {
        dispatcher.dispatch("defecto resuelto " + event.defectId(), () ->
                defectRepository.findByIdWithInspection(event.defectId()).ifPresent(defect -> {
                    // Solo quien reportó un bloqueante está entre los que reciben VEHICLE_AVAILABLE.
                    if (event.vehicleBecameAvailable() && defect.getSeverity() == DefectSeverity.BLOCKING) {
                        return;
                    }
                    UUID reporter = parse(defect.getInspectionAnswer().getInspection().getDriverId());
                    if (reporter == null || userRepository.findById(reporter).isEmpty()) {
                        return;
                    }
                    WorkOrder workOrder = workOrderRepository.findById(event.workOrderId()).orElse(null);
                    notificationService.notify(reporter, NotificationType.DEFECT_RESOLVED,
                            "Se resolvió un defecto que reportaste", message(defect, workOrder), LINK);
                }));
    }

    /** "AF102ZT: se resolvió «Luz de freno». Lámpara reemplazada." */
    private String message(Defect defect, WorkOrder workOrder) {
        UUID vehicleId = defect.getInspectionAnswer().getInspection().getVehicleId();
        String plate = vehicleRepository.findById(vehicleId).map(Vehicle::getPlate).orElse(null);
        String resolved = "se resolvió «" + defect.getDescription() + "».";
        String text = plate == null ? capitalize(resolved) : plate + ": " + resolved;
        String closing = workOrder == null ? null : workOrder.getClosingDescription();
        if (closing == null || closing.isBlank()) {
            return text;
        }
        String trimmed = closing.strip();
        return text + " " + trimmed + (trimmed.matches(".*[.!?…)\"'»]$") ? "" : ".");
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static UUID parse(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
