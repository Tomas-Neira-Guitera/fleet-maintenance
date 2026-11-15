package org.fleetguard.service;

import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripKmAlertReason;
import org.fleetguard.event.TripKmAnomalyEvent;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripInspectionKmRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-152: al cerrar un viaje, revisa si su kilometraje es muy distinto al habitual del vehículo o
 * si el chofer declaró menos km de los que ya tenía. Si es así, marca el viaje y, después del
 * commit, se avisa a los destinatarios de TRIP_KM_ANOMALY. Ver CAM-152-trip-km-alert-contract.md.
 *
 * <p>Los km de un viaje son los del post-viaje menos los del pre-viaje, los dos en su parte entera.
 */
@Service
public class TripKmAlertService {

    private final InspectionRepository inspectionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Rules rules;

    public TripKmAlertService(InspectionRepository inspectionRepository, ApplicationEventPublisher eventPublisher,
                              @Value("${fleetguard.trip-km-alerts.min-trips:3}") int minTrips,
                              @Value("${fleetguard.trip-km-alerts.recent-trips:10}") int recentTrips,
                              @Value("${fleetguard.trip-km-alerts.ratio:2}") double ratio,
                              @Value("${fleetguard.odometer.max-jump-km:5000}") long maxJumpKm) {
        this.inspectionRepository = inspectionRepository;
        this.eventPublisher = eventPublisher;
        this.rules = new Rules(minTrips, recentTrips, ratio, maxJumpKm);
    }

    /**
     * Las reglas, configurables en application.yml (fleetguard.trip-km-alerts).
     *
     * @param minTrips    viajes anteriores que hacen falta para tener un promedio
     * @param recentTrips cuántos de los últimos viajes entran en el promedio
     * @param ratio       un viaje es anormal si recorre más de ratio veces el promedio o menos de 1/ratio
     * @param maxJumpKm   un viaje de más km que esto no entra en el promedio: es un error de carga, la
     *                    misma regla con que el vehículo ignora una lectura (CAM-90)
     */
    record Rules(int minTrips, int recentTrips, double ratio, long maxJumpKm) {
    }

    /** Lo que se marca en el viaje y va en el aviso. */
    record Verdict(TripKmAlertReason reason, Long tripKm, Long averageKm) {
    }

    /**
     * Revisa el viaje que se está cerrando con un post-viaje. Se llama antes de que el km del
     * post-viaje mueva el del vehículo.
     *
     * @param trip       el viaje, ya cerrado y gestionado
     * @param declaredKm el km del post-viaje (parte entera)
     * @param vehicleKm  el km que tenía el vehículo antes del post-viaje
     * @param driverName el chofer que cierra el viaje
     */
    public void check(Trip trip, long declaredKm, long vehicleKm, String driverName) {
        Long preTripKm = inspectionRepository.findFirstByTrip_IdAndType(trip.getId(), InspectionType.PRE_TRIP)
                .map(Inspection::getOdometerKm)
                .map(km -> (long) Math.floor(km))
                .orElse(null);
        List<Long> previous = previousTripKm(trip.getVehicle().getId(), trip.getId());
        Verdict verdict = evaluate(preTripKm, declaredKm, vehicleKm, previous, rules);
        if (verdict == null) {
            return;
        }
        trip.markKmAlert(verdict.reason(), verdict.tripKm(), verdict.averageKm(), declaredKm, vehicleKm);
        eventPublisher.publishEvent(new TripKmAnomalyEvent(trip.getVehicle().getId(), trip.getVehicle().getPlate(),
                driverName, verdict.reason(), verdict.tripKm(), verdict.averageKm(), declaredKm, vehicleKm));
    }

    /** Los km de los viajes cerrados anteriores del vehículo, del más reciente al más viejo. */
    private List<Long> previousTripKm(UUID vehicleId, UUID exceptTripId) {
        Map<UUID, List<TripInspectionKmRow>> byTrip = inspectionRepository.findClosedTripKm(vehicleId, exceptTripId)
                .stream()
                .collect(Collectors.groupingBy(TripInspectionKmRow::tripId));
        record TripKm(Instant endedAt, long km) {
        }
        List<TripKm> trips = new ArrayList<>();
        for (List<TripInspectionKmRow> rows : byTrip.values()) {
            Long pre = km(rows, InspectionType.PRE_TRIP);
            Long post = km(rows, InspectionType.POST_TRIP);
            if (pre != null && post != null && rows.get(0).tripEndedAt() != null) {
                trips.add(new TripKm(rows.get(0).tripEndedAt(), post - pre));
            }
        }
        return trips.stream()
                .sorted(Comparator.comparing(TripKm::endedAt).reversed())
                .map(TripKm::km)
                .toList();
    }

    private static Long km(List<TripInspectionKmRow> rows, InspectionType type) {
        return rows.stream()
                .filter(row -> row.type() == type)
                .map(row -> (long) Math.floor(row.odometerKm()))
                .findFirst()
                .orElse(null);
    }

    /**
     * La regla, sin base de datos.
     *
     * <ul>
     *   <li>Si el chofer declaró menos km de los que ya tenía el vehículo, es BELOW_VEHICLE_KM: un
     *       error de carga, que CAM-90 ignora sin avisar. Gana sobre el promedio, porque con un km
     *       así los km del viaje tampoco son confiables.</li>
     *   <li>El promedio sale de los últimos {@code recentTrips} viajes anteriores con km de pre-viaje
     *       y de post-viaje, sin los de 0 km o menos (no se movió o hay un error) ni los de más de
     *       {@code maxJumpKm}. Hacen falta {@code minTrips}; si no, no hay promedio y no se avisa.</li>
     *   <li>Los km del viaje solo se calculan si el km del pre-viaje es el que tiene el vehículo: es
     *       el que dejó el pre-viaje. Si es otro, CAM-90 lo descartó por mal cargado (menor al actual,
     *       o un salto de más de {@code maxJumpKm}), y restarlo daría un viaje de -90.400 km o de
     *       294.600 que culpa al post-viaje de un error del pre-viaje. Sin km del viaje no se compara
     *       con el promedio.</li>
     *   <li>Con km del viaje, es ABOVE_AVERAGE si recorrió más de {@code ratio} veces el promedio, o
     *       BELOW_AVERAGE si recorrió menos del promedio dividido {@code ratio}. Se compara contra el
     *       promedio ya redondeado, el mismo número que muestra el aviso. Un viaje de 0 km avisa:
     *       puede ser un post-viaje que copió el km del pre-viaje, aunque para el promedio no cuente.</li>
     * </ul>
     *
     * @param previousTripKm los km de los viajes anteriores, del más reciente al más viejo
     * @return null si el viaje es normal
     */
    static Verdict evaluate(Long preTripKm, long declaredKm, long vehicleKm, List<Long> previousTripKm, Rules rules) {
        Long tripKm = preTripKm != null && preTripKm == vehicleKm ? declaredKm - preTripKm : null;
        List<Long> usable = previousTripKm.stream()
                .filter(km -> km > 0 && km <= rules.maxJumpKm())
                .limit(rules.recentTrips())
                .toList();
        Long average = usable.size() < rules.minTrips() ? null
                : Math.round(usable.stream().mapToLong(Long::longValue).average().orElseThrow());

        if (declaredKm < vehicleKm) {
            return new Verdict(TripKmAlertReason.BELOW_VEHICLE_KM, tripKm, average);
        }
        if (tripKm == null || average == null || average == 0) {
            return null;
        }
        if (tripKm > average * rules.ratio()) {
            return new Verdict(TripKmAlertReason.ABOVE_AVERAGE, tripKm, average);
        }
        if (tripKm < average / rules.ratio()) {
            return new Verdict(TripKmAlertReason.BELOW_AVERAGE, tripKm, average);
        }
        return null;
    }
}
