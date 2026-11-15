package org.fleetguard.dto;

/** Body de POST /api/vehicles/{id}/documents/{documentId}/renewals (CAM-127): el documento nuevo. */
public record RenewVehicleDocumentRequest(
        String number,
        String issuedAt,
        String expiresAt,
        String fileUrl
) {
}
