package org.fleetguard.dto;

/** Actividad de un chofer en el período de GET /api/users/stats (CAM-91). defects incluye los bloqueantes. */
public record DriverStatsDto(
        String userId,
        String username,
        boolean active,
        long trips,
        long inspections,
        long defects,
        long blockingDefects
) {
}
