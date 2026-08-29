package org.example.dto;

import java.util.List;

/** Matches components.schemas.VehicleSummary in openapi.yaml exactly. */
public record VehicleSummaryDto(
        String id,
        String plate,
        String brand,
        String model,
        String status,
        List<String> accessories
) {
}
