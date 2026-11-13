package org.fleetguard.dto;

import java.util.List;

/**
 * GET /api/vehicles/{id}/tires (CAM-157): cada posición de su configuración con la cubierta que tiene, y
 * el historial de montajes del vehículo. Las posiciones salen del tipo; sin tipo, positions vacío.
 */
public record VehicleTiresDto(String vehicleId, String plate, String vehicleType, long odometerKm,
                              List<Position> positions, List<TireMountingDto> history) {

    /** axle: 1 el delantero, 0 el auxilio. side: izq|der (null en el auxilio). wheel: simple|exterior|interior. */
    public record Position(String position, String label, int axle, String side, String wheel, TireDto tire) {
    }
}
