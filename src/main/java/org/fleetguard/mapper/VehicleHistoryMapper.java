package org.fleetguard.mapper;

import org.fleetguard.dto.InspectionHistoryItemDto;
import org.fleetguard.dto.MaintenanceHistoryItemDto;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.MaintenanceCompletion;
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
                inspection.isHasBlockingDefect()
        );
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
