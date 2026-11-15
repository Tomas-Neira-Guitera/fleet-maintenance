package org.fleetguard.service;

import org.fleetguard.dto.OccurredOnTripDto;
import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.checklist.ChecklistCatalog;
import org.fleetguard.repository.InspectionRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-146: un defecto del post-viaje ocurrió en el viaje si ese mismo control dio OK en el pre-viaje del viaje.
 * Se calcula con lo ya guardado, sin datos nuevos. Ver docs/api/CAM-146-occurred-on-trip-contract.md.
 */
@Service
public class TripDamageService {

    // "Daños nuevos en la carrocería": su pregunta ya es si apareció algo en el viaje.
    static final String NEW_BODY_DAMAGE_ITEM_ID = "post-danos";

    private final InspectionRepository inspectionRepository;

    public TripDamageService(InspectionRepository inspectionRepository) {
        this.inspectionRepository = inspectionRepository;
    }

    /** Por id de defecto, los que ocurrieron en el viaje. Todo el listado se resuelve con una sola consulta. */
    public Map<UUID, OccurredOnTripDto> occurredOnTrip(Collection<Defect> defects) {
        List<Defect> postTrip = defects.stream()
                .filter(defect -> inspection(defect).getType() == InspectionType.POST_TRIP)
                .toList();
        // HashMap y no Map.of(): get(null) tiene que devolver null, no fallar.
        Map<UUID, OccurredOnTripDto> result = new HashMap<>();
        if (postTrip.isEmpty()) {
            return result;
        }

        Set<UUID> tripIds = postTrip.stream()
                .map(defect -> inspection(defect).getTrip().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Inspection> preTripByTripId = tripIds.isEmpty() ? Map.of()
                : inspectionRepository.findPreTripsWithAnswersByTripIds(tripIds).stream()
                        .collect(Collectors.toMap(preTrip -> preTrip.getTrip().getId(), preTrip -> preTrip,
                                (first, second) -> first));

        for (Defect defect : postTrip) {
            UUID tripId = inspection(defect).getTrip().getId();
            Inspection preTrip = tripId == null ? null : preTripByTripId.get(tripId);
            if (occurredOnTrip(defect.getInspectionAnswer().getItemId(), preTrip)) {
                result.put(defect.getId(), toDto(tripId, preTrip));
            }
        }
        return result;
    }

    /** Un control sin equivalente en el pre-viaje no se marca: no hay contra qué comparar. */
    static boolean occurredOnTrip(String postTripItemId, Inspection preTrip) {
        if (NEW_BODY_DAMAGE_ITEM_ID.equals(postTripItemId)) {
            return true;
        }
        String control = ChecklistCatalog.controlKey(postTripItemId);
        if (control.equals(postTripItemId) || preTrip == null) {
            return false;
        }
        return preTrip.getAnswers().stream()
                .anyMatch(answer -> control.equals(answer.getItemId()) && answer.getOutcome() == CheckOutcome.OK);
    }

    private static OccurredOnTripDto toDto(UUID tripId, Inspection preTrip) {
        if (preTrip == null) {
            return new OccurredOnTripDto(tripId == null ? null : tripId.toString(), null, null, null, null);
        }
        Trip trip = preTrip.getTrip();
        return new OccurredOnTripDto(
                trip.getId().toString(),
                trip.getStartedAt() == null ? null : trip.getStartedAt().toString(),
                trip.getEndedAt() == null ? null : trip.getEndedAt().toString(),
                preTrip.getId() == null ? null : preTrip.getId().toString(),
                preTrip.getTimestamp().toString()
        );
    }

    private static Inspection inspection(Defect defect) {
        return defect.getInspectionAnswer().getInspection();
    }
}
