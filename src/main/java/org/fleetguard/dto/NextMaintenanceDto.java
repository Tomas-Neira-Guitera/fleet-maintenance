package org.fleetguard.dto;

/**
 * Sub-objeto "nextMaintenance" de un FleetStatusRowDto -- el plan más urgente del vehículo.
 * estimatedDueDate (CAM-137) es la fecha aproximada en que vence por km, según lo que anda el
 * vehículo; null si el plan no es por km, si no se puede estimar o si ya venció por km.
 */
public record NextMaintenanceDto(
        String assignmentId,
        String name,
        String status,
        String dueDate,
        Long dueKm,
        Long remainingDays,
        Long remainingKm,
        String estimatedDueDate
) {
}
