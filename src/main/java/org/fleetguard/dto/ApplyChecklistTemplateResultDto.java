package org.fleetguard.dto;

import java.util.List;

/** Resultado de aplicar una plantilla (CAM-150): qué se agregó y qué se salteó por ya existir, por vehículo. */
public record ApplyChecklistTemplateResultDto(String templateId, int added, int skipped, List<VehicleResult> vehicles) {

    public record VehicleResult(String vehicleId, String plate, int added, int skipped, List<String> skippedLabels) {
    }
}
