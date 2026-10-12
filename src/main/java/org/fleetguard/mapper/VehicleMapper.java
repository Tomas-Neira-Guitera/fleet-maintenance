package org.fleetguard.mapper;

import org.fleetguard.dto.CurrentTripDto;
import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.entity.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    /** currentTrip: el viaje abierto del vehículo, o null si no tiene. */
    public VehicleSummaryDto toSummary(Vehicle vehicle, CurrentTripDto currentTrip, boolean blocked) {
        return new VehicleSummaryDto(
                vehicle.getId().toString(),
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                currentTrip != null ? "on-trip" : "available",
                vehicle.getVehicleType(),
                vehicle.getYear(),
                vehicle.getChassisNumber(),
                vehicle.getOdometerKm(),
                vehicle.isActive(),
                blocked,
                currentTrip
        );
    }
}
