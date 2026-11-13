package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.ScheduledMaintenanceAlert;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.repository.ScheduledMaintenanceAlertRepository;
import org.fleetguard.repository.TripRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aviso del turno de taller al chofer (CAM-143). Una vez por día se revisan los turnos programados de hoy
 * (los que todavía no pasaron) y de mañana, y se le avisa al chofer que tiene el vehículo en viaje. Si nadie
 * lo tiene, no se avisa: el chofer que lo elija ve la marca en la lista y la advertencia del pre-viaje.
 * <p>
 * Cada turno se avisa una sola vez por fecha (scheduled_maintenance_alerts). Si se reprograma, cambia su
 * scheduledAt y se vuelve a avisar. Los de hoy se revisan por los viajes abiertos después de la corrida
 * anterior.
 */
@Service
public class ScheduledMaintenanceAlertService {

    static final String LINK = "/";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final ScheduledToday scheduledToday;
    private final TripRepository tripRepository;
    private final ScheduledMaintenanceAlertRepository alertRepository;
    private final NotificationSettingsService settings;
    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public ScheduledMaintenanceAlertService(ScheduledToday scheduledToday, TripRepository tripRepository,
                                            ScheduledMaintenanceAlertRepository alertRepository,
                                            NotificationSettingsService settings,
                                            NotificationService notificationService, NotificationDispatcher dispatcher,
                                            Clock clock) {
        this.scheduledToday = scheduledToday;
        this.tripRepository = tripRepository;
        this.alertRepository = alertRepository;
        this.settings = settings;
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /**
     * La corrida diaria. Con el aviso apagado no se anota nada, así que al prenderlo se avisa lo pendiente.
     * Devuelve cuántos avisos mandó, para los tests.
     */
    @Scheduled(cron = "${fleetguard.scheduled-maintenance-alerts.cron:0 15 8 * * *}", zone = "America/Argentina/Buenos_Aires")
    public int checkAll() {
        if (!settings.effective(NotificationType.SCHEDULED_MAINTENANCE_UPCOMING).enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        LocalDate today = scheduledToday.today();
        List<ScheduledMaintenance> upcoming = new ArrayList<>(scheduledToday.onDay(today));
        upcoming.addAll(scheduledToday.onDay(today.plusDays(1)));
        upcoming.removeIf(schedule -> !schedule.getScheduledAt().isAfter(now));
        if (upcoming.isEmpty()) {
            return 0;
        }
        Map<UUID, Trip> openTripByVehicle = tripRepository.findByStatusWithVehicle(TripStatus.OPEN).stream()
                .filter(trip -> trip.getOpenedById() != null && trip.getVehicle().isActive())
                .collect(Collectors.toMap(trip -> trip.getVehicle().getId(), trip -> trip, (first, second) -> first));

        int sent = 0;
        for (ScheduledMaintenance schedule : upcoming) {
            Trip trip = openTripByVehicle.get(schedule.getVehicleId());
            if (trip != null && send(schedule, trip, today)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean send(ScheduledMaintenance schedule, Trip trip, LocalDate today) {
        if (alertRepository.existsByScheduleIdAndScheduledAt(schedule.getId(), schedule.getScheduledAt())) {
            return false;
        }
        String plate = trip.getVehicle().getPlate();
        // La marca y el aviso van juntos: si el aviso falla, se reintenta en la próxima corrida.
        boolean[] marked = {false};
        boolean committed = dispatcher.dispatch("turno de taller de " + plate, () -> {
            if (alertRepository.existsByScheduleIdAndScheduledAt(schedule.getId(), schedule.getScheduledAt())) {
                return;
            }
            alertRepository.saveAndFlush(new ScheduledMaintenanceAlert(schedule.getId(), schedule.getScheduledAt(),
                    clock.instant()));
            marked[0] = true;
            notificationService.notify(trip.getOpenedById(), NotificationType.SCHEDULED_MAINTENANCE_UPCOMING,
                    "Turno de taller de " + plate, message(plate, schedule, today), LINK);
        });
        return committed && marked[0];
    }

    /** "AB123CD tiene turno de taller mañana 13/11 a las 15:00: «Cambio de aceite». Tenelo en cuenta para volver a tiempo." */
    static String message(String plate, ScheduledMaintenance schedule, LocalDate today) {
        ZonedDateTime at = schedule.getScheduledAt().atZone(ScheduledToday.FLEET_ZONE);
        String day = at.toLocalDate().equals(today) ? "hoy" : "mañana " + DAY.format(at);
        return plate + " tiene turno de taller " + day + " a las " + TIME.format(at) + ": «" + schedule.getTitle()
                + "». Tenelo en cuenta para volver a tiempo.";
    }
}
