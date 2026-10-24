package org.fleetguard.dto;

import java.util.List;

/** Body de PUT /api/notification-settings/{type} (CAM-117): reemplaza la configuración completa del tipo. */
public record UpdateNotificationSettingRequest(
        Boolean enabled,
        List<String> channels,
        List<String> recipientRoles,
        List<String> recipientUserIds,
        Integer leadDays,
        Integer leadKm
) {
}
