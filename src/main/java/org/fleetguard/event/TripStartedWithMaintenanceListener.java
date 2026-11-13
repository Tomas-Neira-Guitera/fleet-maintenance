package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.fleetguard.service.ScheduledToday;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CAM-143: si un vehículo sale de viaje el mismo día (en hora argentina) en que tiene turno de taller, se
 * avisa a los destinatarios configurados (por defecto, los admins). El turno no bloquea la salida: el chofer
 * vio la advertencia y decidió salir igual. Lleva al detalle del vehículo.
 */
@Component
public class TripStartedWithMaintenanceListener {

    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduledToday scheduledToday;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public TripStartedWithMaintenanceListener(ScheduledToday scheduledToday, NotificationService notificationService,
                                              NotificationDispatcher dispatcher) {
        this.scheduledToday = scheduledToday;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener
    public void onTripStarted(TripStartedEvent event) {
        // Un pre-viaje guardado sin señal (CAM-153) que llega al día siguiente ya no es noticia: el texto dice "hoy".
        LocalDate day = event.startedAt().atZone(FLEET_ZONE).toLocalDate();
        if (!day.equals(scheduledToday.today())) {
            return;
        }
        dispatcher.dispatch("salida con turno de " + event.plate(), () ->
                scheduledToday.firstOnDay(event.vehicleId(), day)
                        .ifPresent(schedule -> notificationService.notifyConfiguredRecipients(
                                NotificationType.TRIP_STARTED_WITH_MAINTENANCE,
                                event.plate() + " salió de viaje con turno de taller",
                                message(event, schedule.getScheduledAt().atZone(FLEET_ZONE), schedule.getTitle()),
                                "/vehiculos/" + event.vehicleId())));
    }

    /** "carlos.gomez salió con AB123CD a las 10:20, y tiene turno de taller hoy a las 15:00: «Cambio de aceite»." */
    static String message(TripStartedEvent event, ZonedDateTime scheduledAt, String title) {
        String driver = event.driverName() == null || event.driverName().isBlank() ? "Un chofer" : event.driverName();
        return driver + " salió con " + event.plate() + " a las " + TIME.format(event.startedAt().atZone(FLEET_ZONE))
                + ", y tiene turno de taller hoy a las " + TIME.format(scheduledAt) + ": «" + title + "».";
    }
}
