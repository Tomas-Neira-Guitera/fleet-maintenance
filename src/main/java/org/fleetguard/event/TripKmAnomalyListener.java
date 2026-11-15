package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * CAM-152: después de que se guarda el post-viaje, avisa del kilometraje fuera de lo normal. Un aviso
 * por viaje, que lleva al detalle del vehículo, donde el viaje queda marcado en el historial.
 */
@Component
public class TripKmAnomalyListener {

    private static final Locale AR = Locale.forLanguageTag("es-AR");

    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public TripKmAnomalyListener(NotificationService notificationService, NotificationDispatcher dispatcher) {
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onTripKmAnomaly(TripKmAnomalyEvent event) {
        dispatcher.dispatch("km fuera de lo normal en " + event.plate(), () ->
                notificationService.notifyConfiguredRecipients(NotificationType.TRIP_KM_ANOMALY,
                        "Kilometraje fuera de lo normal en " + event.plate(), message(event),
                        "/vehiculos/" + event.vehicleId()));
    }

    static String message(TripKmAnomalyEvent event) {
        String driver = event.driverName() == null || event.driverName().isBlank() ? "El chofer" : event.driverName();
        return switch (event.reason()) {
            case BELOW_VEHICLE_KM -> driver + " cerró el viaje de " + event.plate() + " con " + km(event.declaredKm())
                    + ", menos de los " + km(event.vehicleKm()) + " que ya tenía el vehículo.";
            case ABOVE_AVERAGE -> driver + " cerró un viaje de " + km(event.tripKm()) + " con " + event.plate()
                    + ", más del doble de su promedio de " + km(event.averageKm()) + " por viaje.";
            case BELOW_AVERAGE -> driver + " cerró un viaje de " + km(event.tripKm()) + " con " + event.plate()
                    + ", menos de la mitad de su promedio de " + km(event.averageKm()) + " por viaje.";
        };
    }

    private static String km(long value) {
        return NumberFormat.getIntegerInstance(AR).format(value) + " km";
    }
}
