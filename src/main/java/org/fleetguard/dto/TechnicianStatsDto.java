package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Actividad de un técnico en GET /api/users/stats (CAM-91). openWorkOrders es la foto de hoy
 * (asignadas o en proceso); el resto, del período. avgResolutionHours: de creada a finalizada,
 * null si no finalizó ninguna.
 */
public record TechnicianStatsDto(
        String userId,
        String username,
        boolean active,
        long openWorkOrders,
        long finalizedWorkOrders,
        Double avgResolutionHours,
        BigDecimal expensesTotal
) {
}
