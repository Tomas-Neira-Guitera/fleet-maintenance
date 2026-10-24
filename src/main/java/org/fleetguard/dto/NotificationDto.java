package org.fleetguard.dto;

/** Coincide con components.schemas.Notification (CAM-96). Fechas en ISO-8601. */
public record NotificationDto(
        String id,
        String type,
        String title,
        String message,
        String link,
        String createdAt,
        String readAt
) {
}
