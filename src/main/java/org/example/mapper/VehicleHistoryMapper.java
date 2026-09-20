package org.example.mapper;

import org.example.dto.InspectionHistoryItemDto;
import org.example.dto.MaintenanceHistoryItemDto;
import org.example.entity.Inspection;
import org.example.entity.MaintenanceCompletion;
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
