package org.fleetguard.dto;

import java.util.List;

/** Coincide con components.schemas.VehicleSummary de openapi.yaml. */
public record VehicleSummaryDto(
        String id,
        String plate,
        String brand,
        String model,
        String status,
        String vehicleType,
        Integer year,
        String chassisNumber,
        long odometerKm,
        boolean active,
        // CAM-89/CAM-127: true si no puede iniciar un viaje (defecto bloqueante abierto o documento vencido).
        boolean blocked,
        // CAM-127: por qué, si blocked; vacío si puede salir.
        List<BlockReasonDto> blockReasons,
        // CAM-87: el viaje abierto, si status es "on-trip"; null si está disponible.
        CurrentTripDto currentTrip
) {
}
