package org.fleetguard.event;

import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** CAM-97: un solo aviso por inspección, aunque traiga varios bloqueantes. Lleva al detalle del vehículo. */
@Component
public class BlockingDefectNotificationListener {

    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public BlockingDefectNotificationListener(NotificationService notificationService, NotificationDispatcher dispatcher) {
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onBlockingDefect(BlockingDefectReportedEvent event) {
        dispatcher.dispatch("defecto bloqueante en " + event.plate(), () ->
                notificationService.notifyConfiguredRecipients(NotificationType.BLOCKING_DEFECT_REPORTED,
                        title(event), message(event), "/vehiculos/" + event.vehicleId()));
    }

    static String title(BlockingDefectReportedEvent event) {
        int count = event.defectTitles().size();
        return count == 1 ? "Defecto bloqueante en " + event.plate()
                : count + " defectos bloqueantes en " + event.plate();
    }

    static String message(BlockingDefectReportedEvent event) {
        String reporter = event.driverName() == null ? "" : " por " + event.driverName();
        // CAM-139: en viaje el vehículo ya salió; queda no disponible para el próximo viaje.
        if (event.inspectionType() == InspectionType.ON_ROAD) {
            return String.join("; ", event.defectTitles()) + ". Reportado durante el viaje" + reporter
                    + ". El viaje sigue abierto, y el vehículo no puede volver a salir hasta resolverlo.";
        }
        String inspection = event.inspectionType() == InspectionType.PRE_TRIP ? "pre-viaje" : "post-viaje";
        return String.join("; ", event.defectTitles()) + ". Reportado en el " + inspection + reporter
                + ". El vehículo no puede salir hasta resolverlo.";
    }
}
