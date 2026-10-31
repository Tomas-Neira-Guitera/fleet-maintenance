package org.fleetguard.dto;

import java.util.List;

/**
 * GET /api/vehicles/{id}/documents (CAM-127): los vigentes ordenados por vencimiento, los renovados
 * (historial, el más nuevo primero), los obligatorios que faltan y la anticipación de "por vencer".
 */
public record VehicleDocumentsDto(
        List<VehicleDocumentDto> items,
        List<VehicleDocumentDto> history,
        List<String> missingRequired,
        int leadDays
) {
}
