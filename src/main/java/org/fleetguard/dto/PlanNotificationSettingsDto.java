package org.fleetguard.dto;

import java.util.List;

/**
 * GET/PUT/DELETE /api/maintenance-plans/{id}/notifications (CAM-118). custom=false: el plan usa la
 * configuración general, que viene en general para mostrarla; en ese caso enabled/channels/lead* son null.
 */
public record PlanNotificationSettingsDto(
        String planId,
        String intervalType,
        boolean custom,
        Boolean enabled,
        List<String> channels,
        Integer leadDays,
        Integer leadKm,
        General general
) {
    public record General(boolean dueSoonEnabled, boolean overdueEnabled, List<String> channels,
                          Integer leadDays, Integer leadKm) {
    }
}
