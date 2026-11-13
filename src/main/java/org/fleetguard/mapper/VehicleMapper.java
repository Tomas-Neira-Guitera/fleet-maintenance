package org.fleetguard.mapper;

import org.fleetguard.dto.BlockReasonDto;
import org.fleetguard.dto.CurrentTripDto;
import org.fleetguard.dto.VehicleSummaryDto;
import org.fleetguard.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class VehicleMapper {

    /** currentTrip: el viaje abierto del vehículo, o null si no tiene. scheduledToday: el turno de hoy, o null (CAM-143). */
    public VehicleSummaryDto toSummary(Vehicle vehicle, CurrentTripDto currentTrip, List<BlockReasonDto> blockReasons,
                                       Instant scheduledToday) {
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
                !blockReasons.isEmpty(),
                blockReasons,
                currentTrip,
                scheduledToday == null ? null : scheduledToday.toString()
        );
    }
}
