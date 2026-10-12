package org.fleetguard.dto;

import java.util.List;

/** Respuesta de GET /api/users/stats (CAM-91): actividad por usuario, separada por rol. */
public record UserStatsDto(List<DriverStatsDto> drivers, List<TechnicianStatsDto> technicians) {
}
