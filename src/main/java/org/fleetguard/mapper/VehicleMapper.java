package org.fleetguard.mapper;

import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.entity.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    public VehicleSummaryDto toSummary(Vehicle vehicle, boolean onTrip) {
        return new VehicleSummaryDto(
                vehicle.getId().toString(),
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                onTrip ? "on-trip" : "available",
                vehicle.getVehicleType(),
                vehicle.getYear(),
                vehicle.getChassisNumber(),
                vehicle.getOdometerKm(),
                vehicle.isActive()
        );
    }
}
