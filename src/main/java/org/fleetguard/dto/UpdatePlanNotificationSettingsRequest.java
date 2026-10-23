package org.fleetguard.dto;

import java.util.List;

/** Body de PUT /api/maintenance-plans/{id}/notifications (CAM-118). */
public record UpdatePlanNotificationSettingsRequest(
        Boolean enabled,
        List<String> channels,
        Integer leadDays,
        Integer leadKm
) {
}
