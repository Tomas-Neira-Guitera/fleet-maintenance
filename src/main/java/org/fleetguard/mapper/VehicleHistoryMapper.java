package org.fleetguard.mapper;

import org.fleetguard.dto.InspectionHistoryItemDto;
import org.fleetguard.dto.MaintenanceHistoryItemDto;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.MaintenanceCompletion;
import org.springframework.stereotype.Component;

@Component
public class VehicleHistoryMapper {

    public InspectionHistoryItemDto toInspectionItem(Inspection inspection) {
        return new InspectionHistoryItemDto(
                inspection.getId().toString(),
                inspection.getType().toJson(),
                inspection.getTimestamp().toString(),
                inspection.getDriverName(),
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
