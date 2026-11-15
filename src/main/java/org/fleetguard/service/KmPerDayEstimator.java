package org.fleetguard.service;

import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CAM-137: cuántos km por día anda un vehículo, para estimar cuándo vence un plan por km. La
 * estimación solo se muestra: el estado del plan, el healthScore y los avisos siguen saliendo de
 * los km reales. Ver CAM-40-fleet-status-contract.md, "Fecha estimada".
 *
 * <p>Las lecturas salen de las inspecciones y de los mantenimientos hechos, lo único que guarda el
 * km con su fecha. Una OT de un plan, al finalizarse, crea un mantenimiento hecho y cuenta; "Cargar
 * km" y las OTs de un defecto o manuales no dejan registro del km.
 */
@Service
public class KmPerDayEstimator {

    /** Las lecturas de los últimos 60 días son las que cuentan para el promedio. */
    static final Duration WINDOW = Duration.ofDays(60);
    /** Entre la primera y la última lectura válida de la ventana tiene que haber al menos una semana. */
    static final Duration MIN_SPAN = Duration.ofDays(7);
    /**
     * Más de esto sostenido durante una semana no lo hace un camión: unas 18 horas de manejo por
     * día. Un promedio mayor sale de una lectura mal cargada.
     */
    static final double MAX_KM_PER_DAY = 1500;
    /** El de {@code fleetguard.odometer.max-jump-km} si no se configura (OdometerReadings). */
    static final long DEFAULT_MAX_JUMP_KM = 5000;

    private static final double SECONDS_PER_DAY = 86_400d;
    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final InspectionRepository inspectionRepository;
    private final MaintenanceCompletionRepository completionRepository;
    private final long maxJumpKm;

    @Autowired
    public KmPerDayEstimator(InspectionRepository inspectionRepository,
                             MaintenanceCompletionRepository completionRepository,
                             @Value("${fleetguard.odometer.max-jump-km:5000}") long maxJumpKm) {
        this.inspectionRepository = inspectionRepository;
        this.completionRepository = completionRepository;
        this.maxJumpKm = maxJumpKm;
    }

    /** Con el salto máximo por defecto, para tests. */
    public KmPerDayEstimator(InspectionRepository inspectionRepository,
                             MaintenanceCompletionRepository completionRepository) {
        this(inspectionRepository, completionRepository, DEFAULT_MAX_JUMP_KM);
    }

    /** Una lectura de km y cuándo se hizo. */
    record Reading(Instant at, long km) {
    }

    /** Los km por día de cada vehículo, con dos consultas para toda la flota. Sin estimación, el vehículo no está. */
    public Map<UUID, Double> kmPerDay(Collection<Vehicle> vehicles, Instant now) {
        if (vehicles.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Reading>> readings = readings(vehicles);
        Map<UUID, Double> result = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            Double pace = kmPerDay(readings.getOrDefault(vehicle.getId(), List.of()), vehicle.getOdometerKm(), now,
                    maxJumpKm);
            if (pace != null) {
                result.put(vehicle.getId(), pace);
            }
        }
        return result;
    }

    /**
     * Las lecturas de km de cada vehículo, sin filtrar, con dos consultas para toda la flota. También las usa
     * la comparación de vehículos (CAM-140), que las filtra con {@link #validReadings}.
     */
    Map<UUID, List<Reading>> readings(Collection<Vehicle> vehicles) {
        if (vehicles.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = vehicles.stream().map(Vehicle::getId).toList();
        // Inspección: el km con decimales se redondea para abajo, como en el costo por km (CAM-105).
        // Mantenimiento hecho: cuenta desde la medianoche argentina de su día.
        Stream<Map.Entry<UUID, Reading>> fromInspections = inspectionRepository.findKmReadings(ids).stream()
                .map(row -> Map.entry(row.vehicleId(),
                        new Reading(row.timestamp(), (long) Math.floor(row.odometerKm()))));
        Stream<Map.Entry<UUID, Reading>> fromCompletions = completionRepository.findKmReadings(ids).stream()
                .map(row -> Map.entry(row.vehicleId(),
                        new Reading(row.completedAt().atStartOfDay(FLEET_ZONE).toInstant(), row.completedKm())));
        return Stream.concat(fromInspections, fromCompletions)
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    /**
     * Las lecturas que valen, en orden: sin las menores a una anterior ni las mayores al km actual del
     * vehículo, ni las futuras (ver {@link #kmPerDay(List, long, Instant, long)}).
     */
    static List<Reading> validReadings(List<Reading> readings, long currentKm, Instant now) {
        List<Reading> sorted = readings.stream()
                .sorted(Comparator.comparing((Reading reading) -> reading.at().atZone(FLEET_ZONE).toLocalDate())
                        .thenComparingLong(Reading::km)
                        .thenComparing(Reading::at))
                .toList();
        List<Reading> valid = new ArrayList<>();
        long maxSoFar = Long.MIN_VALUE;
        for (Reading reading : sorted) {
            if (reading.km() < maxSoFar || reading.km() > currentKm || reading.at().isAfter(now)) {
                continue;
            }
            maxSoFar = reading.km();
            valid.add(reading);
        }
        return valid;
    }

    /**
     * Los km por día de un vehículo, o null si no se pueden estimar.
     *
     * <ul>
     *   <li>Las lecturas se recorren por fecha, desde la primera de su historia, y se ignora la que
     *       tiene menos km que una anterior: el odómetro no baja, así que es un error de carga
     *       (AA846JQ tiene inspecciones con 310 y 15.000 km siendo un vehículo de 309.400, CAM-90).
     *       Se mira toda la historia, y no solo la ventana, para que una lectura mal cargada al
     *       principio de la ventana no pase por buena.</li>
     *   <li>También se ignora la que supera el km actual del vehículo: es un error de tipeo que el
     *       vehículo no aceptó (CAM-90, salto máximo).</li>
     *   <li>Dentro de un mismo día manda el km y no la hora: un mantenimiento hecho solo guarda el
     *       día y cuenta desde las 00:00, así que quedaría antes del pre-viaje de esa mañana, que
     *       tiene menos km, y lo haría pasar por un error.</li>
     *   <li>De las que quedan, cuentan las de los últimos 60 días. Hacen falta dos o más, con al
     *       menos 7 días entre la primera y la última, y un recorrido mayor a 0.</li>
     *   <li>La primera puede ser un error que ninguna lectura anterior delata (un vehículo dado de
     *       alta con 309.000 km, sin lectura, cuya primera inspección se cargó con 310 o con
     *       290.000). Se descarta y se prueba con la siguiente si hay más de {@code maxJumpKm} hasta
     *       la lectura que le sigue (la misma regla con que el vehículo ignora una lectura, CAM-90:
     *       nadie hace más de 5.000 km entre dos lecturas), o si hasta la última da más de
     *       {@link #MAX_KM_PER_DAY} km por día.</li>
     * </ul>
     */
    static Double kmPerDay(List<Reading> readings, long currentKm, Instant now) {
        return kmPerDay(readings, currentKm, now, DEFAULT_MAX_JUMP_KM);
    }

    static Double kmPerDay(List<Reading> readings, long currentKm, Instant now, long maxJumpKm) {
        List<Reading> valid = validReadings(readings, currentKm, now);

        Instant windowStart = now.minus(WINDOW);
        List<Reading> inWindow = valid.stream().filter(reading -> !reading.at().isBefore(windowStart)).toList();
        if (inWindow.size() < 2) {
            return null;
        }
        Reading last = inWindow.get(inWindow.size() - 1);
        for (int i = 0; i < inWindow.size() - 1; i++) {
            Reading first = inWindow.get(i);
            if (inWindow.get(i + 1).km() - first.km() > maxJumpKm) {
                continue;
            }
            Duration span = Duration.between(first.at(), last.at());
            long driven = last.km() - first.km();
            if (span.compareTo(MIN_SPAN) < 0 || driven <= 0) {
                // Las siguientes están más cerca de la última: tampoco llegan a una semana o a moverse.
                return null;
            }
            double pace = driven / (span.getSeconds() / SECONDS_PER_DAY);
            if (pace <= MAX_KM_PER_DAY) {
                return pace;
            }
        }
        return null;
    }

    /**
     * Hoy + km que faltan / km por día, redondeado para arriba. Null sin estimación o si el plan
     * ya venció por km: un plan vencido no tiene una fecha por delante.
     */
    static LocalDate estimatedDueDate(Long remainingKm, Double kmPerDay, LocalDate today) {
        if (remainingKm == null || kmPerDay == null || remainingKm <= 0 || kmPerDay <= 0) {
            return null;
        }
        // El margen evita que un error de punto flotante (1000 / 99,99999) sume un día de más.
        return today.plusDays((long) Math.ceil(remainingKm / kmPerDay - 1e-6));
    }
}
