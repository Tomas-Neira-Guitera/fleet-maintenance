package org.fleetguard.mapper;

import org.fleetguard.dto.InspectionHistoryItemDto;
import org.fleetguard.dto.MaintenanceHistoryItemDto;
import org.fleetguard.dto.TripKmAlertDto;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.Trip;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VehicleHistoryMapper {

    /** currentNames: nombre actual por id de usuario (UserNameService); sin dato, el guardado en la inspección. */
    public InspectionHistoryItemDto toInspectionItem(Inspection inspection, Map<String, String> currentNames) {
        return new InspectionHistoryItemDto(
                inspection.getId().toString(),
                inspection.getType().toJson(),
                inspection.getTimestamp().toString(),
                currentNames.getOrDefault(inspection.getDriverId(), inspection.getDriverName()),
                inspection.getOdometerKm(),
                inspection.getNotes(),
                inspection.isHasBlockingDefect(),
                kmAlert(inspection)
        );
    }

    /** CAM-152: la marca va en el post-viaje, que es el que cerró el viaje y cargó el km. */
    private static TripKmAlertDto kmAlert(Inspection inspection) {
        Trip trip = inspection.getTrip();
        if (inspection.getType() != InspectionType.POST_TRIP || trip == null || trip.getKmAlertReason() == null) {
            return null;
        }
        return new TripKmAlertDto(trip.getKmAlertReason().toJson(), trip.getKmAlertTripKm(),
                trip.getKmAlertAverageKm(), trip.getKmAlertDeclaredKm(), trip.getKmAlertVehicleKm());
    }

    public MaintenanceHistoryItemDto toMaintenanceItem(MaintenanceCompletion completion) {
        return new MaintenanceHistoryItemDto(
                completion.getId().toString(),
                completion.getAssignment().getMaintenancePlan().getName(),
                completion.getCompletedAt().toString(),
                completion.getCompletedKm(),
                completion.getWorkOrderId(),
                completion.getNotes()
        );
    }
}
