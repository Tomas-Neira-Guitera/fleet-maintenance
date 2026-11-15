package org.fleetguard.dto;

import java.util.List;

/**
 * Un tipo de aviso en GET /api/notification-settings (CAM-117). audience: "owner" (le llega a la
 * persona involucrada, recipientDescription dice a quién) o "configurable" (los destinatarios elegidos).
 */
public record NotificationSettingDto(
        String type,
        String label,
        String description,
        String audience,
        String recipientDescription,
        boolean enabled,
        List<String> channels,
        List<String> recipientRoles,
        List<String> recipientUserIds,
        boolean supportsLead,
        // CAM-127: los documentos se anticipan solo en días; los planes, en días y km.
        boolean supportsLeadKm,
        Integer leadDays,
        Integer leadKm,
        boolean customized
) {
}
