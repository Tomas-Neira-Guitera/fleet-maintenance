package org.fleetguard.dto;

/** Body de POST /api/vehicles/{id}/documents (CAM-127). name solo para el tipo "otro". Fechas YYYY-MM-DD. */
public record CreateVehicleDocumentRequest(
        String type,
        String name,
        String number,
        String issuedAt,
        String expiresAt,
        String fileUrl
) {
}
