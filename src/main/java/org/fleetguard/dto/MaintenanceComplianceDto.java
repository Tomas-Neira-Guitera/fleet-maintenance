package org.fleetguard.dto;

import java.time.Instant;

/** GET /api/maintenance-compliance (CAM-155). Coincide con components.schemas.MaintenanceCompliance. */
public record MaintenanceComplianceDto(
        Instant from,
        Instant to,
        Preventive preventive,
        DefectResolution defects,
        OpenWorkOrders openWorkOrders
) {
    /** Mantenimientos hechos en el período, a tiempo o después de vencer. onTimePct es null si no hay ninguno. */
    public record Preventive(int onTime, int late, Double onTimePct) {
    }

    /** Defectos resueltos en el período y las horas promedio desde que se reportaron. */
    public record DefectResolution(int resolved, Double avgHours) {
    }

    /** OTs abiertas hoy (asignadas o en proceso), por antigüedad. No depende del período. */
    public record OpenWorkOrders(int total, int under7Days, int from7To30Days, int over30Days) {
    }
}
