package org.fleetguard.mapper;

import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.dto.CouplingDto;
import org.fleetguard.dto.CurrentTripDto;
import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VehicleMapper {

    /** currentTrip: el viaje abierto del vehículo, o null si no tiene. */
    public VehicleSummaryDto toSummary(Vehicle vehicle, CurrentTripDto currentTrip, List<BlockReasonDto> blockReasons) {
        return toSummary(vehicle, currentTrip, blockReasons, null);
    }

    public VehicleSummaryDto toSummary(Vehicle vehicle, CurrentTripDto currentTrip, List<BlockReasonDto> blockReasons,
                                       CouplingDto.VehicleRef coupledWith) {
        return new VehicleSummaryDto(
                vehicle.getId().toString(),
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                currentTrip != null ? "on-trip" : "available",
                VehicleType.toJson(vehicle.getVehicleType()),
                vehicle.getYear(),
                vehicle.getChassisNumber(),
                vehicle.getOdometerKm(),
                vehicle.isActive(),
                !blockReasons.isEmpty(),
                blockReasons,
                currentTrip,
                coupledWith
        );
    }
}
