package org.fleetguard.dto;

import java.util.List;

/** Resultado de quitar una plantilla (CAM-159): qué controles se borraron de cada vehículo. */
public record UnapplyChecklistTemplateResultDto(String templateId, int removed, List<VehicleResult> vehicles) {

    public record VehicleResult(String vehicleId, String plate, int removed, List<String> removedLabels) {
    }
}
