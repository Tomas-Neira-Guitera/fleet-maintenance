package org.fleetguard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/vehicles/{id}/tires (CAM-157): cada posición de su configuración con la cubierta que tiene, y
 * el historial de montajes del vehículo. layout null: todavía sin configurar (positions vacío).
 */
public record VehicleTiresDto(String vehicleId, String plate, String layout, long odometerKm, BigDecimal minTreadMm,
                              List<Position> positions, List<TireMountingDto> history) {

    /** axle: 1 delantero, 0 auxilio. side: izq|der (null en el auxilio). */
    public record Position(String position, String label, int axle, String side, boolean inner, TireDto tire) {
    }
}
