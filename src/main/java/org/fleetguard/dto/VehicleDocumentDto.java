package org.fleetguard.dto;

/**
 * Un documento de un vehículo (CAM-127). status: "al_dia", "por_vencer", "vencido" o "sin_vencimiento".
 * daysToExpiry: días que faltan (negativo si venció). current: false si ya se renovó (historial).
 */
public record VehicleDocumentDto(
        String id,
        String vehicleId,
        String type,
        String typeLabel,
        String name,
        String displayName,
        String number,
        String issuedAt,
        String expiresAt,
        String fileUrl,
        String status,
        Long daysToExpiry,
        boolean current,
        String replacedById,
        String createdAt
) {
}
