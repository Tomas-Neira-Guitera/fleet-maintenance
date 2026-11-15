package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** CAM-156: después de guardar la carga, avisa del consumo anormal. Lleva a la pestaña Combustible del vehículo. */
@Component
public class FuelConsumptionAnomalyListener {

    private static final Locale AR = Locale.forLanguageTag("es-AR");

    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public FuelConsumptionAnomalyListener(NotificationService notificationService, NotificationDispatcher dispatcher) {
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onAnomaly(FuelConsumptionAnomalyEvent event) {
        dispatcher.dispatch("consumo anormal de combustible en " + event.plate(), () ->
                notificationService.notifyConfiguredRecipients(NotificationType.FUEL_CONSUMPTION_ANOMALY,
                        "Consumo de combustible alto en " + event.plate(), message(event),
                        "/vehiculos/" + event.vehicleId() + "/combustible"));
    }

    static String message(FuelConsumptionAnomalyEvent event) {
        NumberFormat decimal = NumberFormat.getNumberInstance(AR);
        decimal.setMinimumFractionDigits(1);
        decimal.setMaximumFractionDigits(1);
        BigDecimal percent = event.litersPer100Km().subtract(event.averageLitersPer100Km())
                .multiply(BigDecimal.valueOf(100))
                .divide(event.averageLitersPer100Km(), 0, RoundingMode.HALF_UP);
        return event.plate() + " consumió " + decimal.format(event.litersPer100Km()) + " L cada 100 km en los últimos "
                + NumberFormat.getIntegerInstance(AR).format(event.km()) + " km, un " + percent
                + "% más que su promedio de " + decimal.format(event.averageLitersPer100Km()) + " L.";
    }
}
