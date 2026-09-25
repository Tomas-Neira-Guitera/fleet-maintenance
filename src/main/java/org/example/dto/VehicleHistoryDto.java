package org.example.dto;

import java.util.List;

/** Coincide con components.schemas.VehicleHistory de openapi.yaml. */
public record VehicleHistoryDto(
        List<InspectionHistoryItemDto> inspections,
        List<DefectDto> defects,
        List<MaintenanceHistoryItemDto> maintenance
) {
}
