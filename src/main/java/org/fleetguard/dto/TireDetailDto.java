package org.fleetguard.dto;

import java.util.List;

/** GET /api/tires/{id} (CAM-157): la cubierta, dónde estuvo y lo que se reportó de ella. */
public record TireDetailDto(TireDto tire, List<TireMountingDto> mountings, List<TireConditionReportDto> reports) {
}
