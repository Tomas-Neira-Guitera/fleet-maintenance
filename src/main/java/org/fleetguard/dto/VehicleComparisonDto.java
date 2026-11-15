package org.fleetguard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/vehicles/comparison (CAM-140): una fila por vehículo activo para decidir si conviene
 * reparar o reemplazar, y el promedio de la flota. Ver CAM-140-vehicle-comparison-contract.md.
 */
public record VehicleComparisonDto(Instant from, Instant to, List<Row> vehicles, Averages fleet) {

    /**
     * kmDriven y los costos por km son null sin lecturas de km suficientes en el período (no un número
     * engañoso); el costo por km también es null con 0 km. availabilityPct es null si el período no dura nada.
     */
    public record Row(String vehicleId, String plate, String brand, String model, String vehicleType, Integer year,
                      BigDecimal maintenanceCost, BigDecimal operatingCost, BigDecimal totalCost, Long kmDriven,
                      BigDecimal maintenanceCostPerKm, BigDecimal totalCostPerKm, Double availabilityPct,
                      double downtimeHours, int stops, int recurringControls, Review review) {
    }

    /**
     * "Para revisar": peor que el promedio en al menos dos de los tres indicadores. reasons dice cuáles:
     * maintenance_cost_per_km, availability, recurring_defects.
     */
    public record Review(boolean flagged, List<String> reasons) {
    }

    /** El promedio de cada indicador sobre los vehículos que lo tienen; null si ninguno lo tiene. */
    public record Averages(BigDecimal maintenanceCost, BigDecimal operatingCost, BigDecimal totalCost, Double kmDriven,
                           BigDecimal maintenanceCostPerKm, BigDecimal totalCostPerKm, Double availabilityPct,
                           Double downtimeHours, Double stops, Double recurringControls) {
    }
}
