package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.util.Locale;

/** CAM-157: después del commit, avisa del dibujo bajo. Lleva a la pestaña Neumáticos del vehículo. */
@Component
public class TireTreadLowListener {

    private static final Locale AR = Locale.forLanguageTag("es-AR");

    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public TireTreadLowListener(NotificationService notificationService, NotificationDispatcher dispatcher) {
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onTreadLow(TireTreadLowEvent event) {
        dispatcher.dispatch("dibujo bajo en " + event.plate(), () ->
                notificationService.notifyConfiguredRecipients(NotificationType.TIRE_TREAD_LOW,
                        "Dibujo bajo en " + event.plate(), message(event),
                        "/vehiculos/" + event.vehicleId() + "/neumaticos"));
    }

    static String message(TireTreadLowEvent event) {
        NumberFormat mm = NumberFormat.getNumberInstance(AR);
        mm.setMinimumFractionDigits(1);
        mm.setMaximumFractionDigits(1);
        String who = event.measuredBy() == null || event.measuredBy().isBlank() ? "" : " Lo midió " + event.measuredBy() + ".";
        return "La cubierta " + event.tireLabel() + " (" + event.positionLabel() + ") de " + event.plate() + " tiene "
                + mm.format(event.depthMm()) + " mm de dibujo, menos del mínimo de " + mm.format(event.minMm()) + " mm."
                + who;
    }
}
