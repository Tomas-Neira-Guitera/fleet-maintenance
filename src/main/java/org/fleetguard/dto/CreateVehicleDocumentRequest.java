package org.fleetguard.dto;

/**
 * Body de POST /api/vehicles/{id}/documents (CAM-127). name es libre y obligatorio; el tipo sale del nombre
 * ("Póliza La Segunda" es el seguro). type es opcional y lo fuerza. Fechas YYYY-MM-DD.
 */
public record CreateVehicleDocumentRequest(
        String type,
        String name,
        String number,
        String issuedAt,
        String expiresAt,
        String fileUrl
) {
}
