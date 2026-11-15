package org.fleetguard.dto;

/**
 * Coincide con components.schemas.RoadDefectRequest de openapi.yaml -- CAM-139. itemId es un control
 * del pre-viaje del vehículo o "road-otro"; defect sigue las mismas reglas que en el checklist.
 */
public record RoadDefectRequest(String itemId, DefectDetailDto defect) {
}
