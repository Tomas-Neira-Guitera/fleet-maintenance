package org.fleetguard.dto;

import java.util.List;

/**
 * CAM-157: un relevamiento de una cubierta. severity es la del defecto que lo acompaña (blocking|non-blocking).
 * active: el defecto sigue abierto y la cubierta montada.
 */
public record TireConditionReportDto(String vehicleId, String plate, String position, String positionLabel,
                                     List<String> conditions, List<String> conditionLabels, String severity,
                                     String reportedAt, String reportedBy, String defectId, boolean active) {
}
