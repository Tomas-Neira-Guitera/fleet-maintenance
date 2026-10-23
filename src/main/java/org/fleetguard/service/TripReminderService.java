package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Trip;
import org.fleetguard.repository.TripRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CAM-101: si el chofer se olvidó de hacer el post-viaje, el viaje queda abierto y el vehículo
 * bloqueado. Cada hora se busca los viajes abiertos hace más de
 * {@code fleetguard.trips.reminder-after-hours} y se le recuerda al chofer que lo abrió, una sola
 * vez por viaje. No nace de un evento sino del paso del tiempo, así que avisa directo, sin listener.
 */
@Service
public class TripReminderService {

    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter STARTED_AT_FORMAT = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

    private final TripRepository tripRepository;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;
    private final Duration remindAfter;

    public TripReminderService(TripRepository tripRepository, NotificationService notificationService,
                               NotificationDispatcher dispatcher, Clock clock,
                               @Value("${fleetguard.trips.reminder-after-hours:12}") long reminderAfterHours) {
        this.tripRepository = tripRepository;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.remindAfter = Duration.ofHours(reminderAfterHours);
    }

    /** Devuelve cuántos recordatorios mandó; el valor lo ignora el scheduler, sirve para los tests. */
    @Scheduled(cron = "${fleetguard.trips.reminder-cron:0 0 * * * *}")
    public int sendReminders() {
        Instant now = clock.instant();
        int sent = 0;
        for (Trip trip : tripRepository.findOpenTripsToRemind(now.minus(remindAfter))) {
            // La marca y el aviso van juntos en la transacción del envío. Primero la marca: si el chofer
            // cerró el viaje desde que se leyó (u otra corrida ya lo marcó), no se avisa. Si el aviso
            // falla, la marca se deshace y se vuelve a intentar en la corrida siguiente.
            boolean[] marked = {false};
            boolean committed = dispatcher.dispatch("recordatorio del viaje " + trip.getId(), () -> {
                marked[0] = tripRepository.markReminderSent(trip.getId(), now) == 1;
                if (marked[0]) {
                    notificationService.notify(trip.getOpenedById(), NotificationType.TRIP_OPEN_REMINDER,
                            "Acordate de cerrar el viaje", message(trip), "/");
                }
            });
            if (committed && marked[0]) {
                sent++;
            }
        }
        return sent;
    }

    private static String message(Trip trip) {
        String startedAt = STARTED_AT_FORMAT.format(trip.getStartedAt().atZone(FLEET_ZONE));
        return "Tenés abierto el viaje de " + trip.getVehicle().getPlate() + " desde el " + startedAt
                + ". Acordate de hacer el post-viaje.";
    }
}
