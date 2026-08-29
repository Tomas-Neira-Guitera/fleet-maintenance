package org.example.mapper;

import org.example.dto.VehicleSummaryDto;
import org.example.entity.Vehicle;
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
                vehicle.getAccessories()
        );
    }
}
