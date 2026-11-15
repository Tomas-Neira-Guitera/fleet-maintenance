package org.fleetguard.dto;

import java.util.List;

/** Respuesta de POST /api/vehicles/import (CAM-154). Coincide con components.schemas.VehicleImportResult. */
public record VehicleImportResultDto(
        int total,
        int valid,
        // Cuántos se crearon: 0 en la vista previa (dryRun).
        int created,
        List<Row> rows
) {
    /** row: posición en el pedido, desde 1. vehicleId: el creado, o null en la vista previa y en las filas con errores. */
    public record Row(int row, String plate, boolean valid, List<FieldValidationErrorDetail> errors, String vehicleId) {
    }
}
