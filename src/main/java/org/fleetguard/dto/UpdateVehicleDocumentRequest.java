package org.fleetguard.dto;

/**
 * Body de PATCH /api/vehicles/{id}/documents/{documentId} (CAM-127): corrige un dato mal cargado.
 * Ausente no cambia nada; "" borra el número, las fechas o el archivo.
 */
public record UpdateVehicleDocumentRequest(
        String name,
        String number,
        String issuedAt,
        String expiresAt,
        String fileUrl
) {
}
