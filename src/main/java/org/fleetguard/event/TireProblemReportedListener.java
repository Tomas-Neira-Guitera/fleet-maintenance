package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** CAM-157: después del commit, avisa de las cubiertas con problemas. Lleva a la pestaña Neumáticos del vehículo. */
@Component
public class TireProblemReportedListener {

    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public TireProblemReportedListener(NotificationService notificationService, NotificationDispatcher dispatcher) {
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onReported(TireProblemReportedEvent event) {
        dispatcher.dispatch("cubiertas con problemas en " + event.plate(), () ->
                notificationService.notifyConfiguredRecipients(NotificationType.TIRE_PROBLEM_REPORTED,
                        title(event), message(event), "/vehiculos/" + event.vehicleId() + "/neumaticos"));
    }

    static String title(TireProblemReportedEvent event) {
        return (event.tires().size() == 1 ? "Cubierta con problemas en " : "Cubiertas con problemas en ") + event.plate();
    }

    static String message(TireProblemReportedEvent event) {
        String who = event.reportedBy() == null || event.reportedBy().isBlank() ? "Se reportó" : event.reportedBy() + " reportó";
        return who + " en " + event.plate() + ": " + String.join("; ", event.tires()) + ".";
    }
}
