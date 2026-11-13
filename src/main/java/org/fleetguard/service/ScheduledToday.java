package org.fleetguard.service;

import org.fleetguard.entity.ScheduleStatus;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * El turno de taller de hoy de cada vehículo (CAM-143): el programado más temprano del día, en hora
 * argentina (CAM-88), aunque ya haya pasado la hora y no se haya marcado hecho. Lo usan el listado de
 * vehículos y el aviso al admin cuando un vehículo sale de viaje con turno.
 */
@Component
public class ScheduledToday {

    static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final ScheduledMaintenanceRepository repository;
    private final Clock clock;

    public ScheduledToday(ScheduledMaintenanceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** De toda la flota, en una sola consulta. Los vehículos sin turno hoy no están. */
    public Map<UUID, Instant> forAll() {
        Map<UUID, Instant> earliest = new HashMap<>();
        for (ScheduledMaintenance schedule : onDay(today())) {
            earliest.putIfAbsent(schedule.getVehicleId(), schedule.getScheduledAt());
        }
        return earliest;
    }

    public Instant forVehicle(UUID vehicleId) {
        return forVehicle(vehicleId, today());
    }

    /** El turno de ese vehículo en ese día, o null. */
    public Instant forVehicle(UUID vehicleId, LocalDate day) {
        return firstOnDay(vehicleId, day).map(ScheduledMaintenance::getScheduledAt).orElse(null);
    }

    /** El turno completo, para quien necesita el título (el aviso al admin). */
    public Optional<ScheduledMaintenance> firstOnDay(UUID vehicleId, LocalDate day) {
        return repository.findByVehicleIdAndScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(
                vehicleId, start(day), end(day), List.of(ScheduleStatus.SCHEDULED)).stream().findFirst();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(FLEET_ZONE));
    }

    /** Programados de ese día, del más temprano al más tarde. */
    List<ScheduledMaintenance> onDay(LocalDate day) {
        return repository.findByScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(
                start(day), end(day), List.of(ScheduleStatus.SCHEDULED));
    }

    static Instant start(LocalDate day) {
        return day.atStartOfDay(FLEET_ZONE).toInstant();
    }

    /**
     * "Between" incluye los dos extremos: el último microsegundo del día, no el comienzo del siguiente. En
     * microsegundos y no en nanos porque Postgres redondea a microsegundos, y 23:59:59.999999999 pasaría a
     * ser las 00:00 del día siguiente.
     */
    static Instant end(LocalDate day) {
        return start(day.plusDays(1)).minus(1, ChronoUnit.MICROS);
    }
}
