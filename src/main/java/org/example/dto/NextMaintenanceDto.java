package org.example.dto;

/** Sub-objeto "nextMaintenance" de un FleetStatusRowDto -- el plan más urgente del vehículo. */
public record NextMaintenanceDto(
        String assignmentId,
        String name,
        String status,
        String dueDate,
        Long dueKm,
        Long remainingDays,
        Long remainingKm
) {
}
