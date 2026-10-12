package org.fleetguard.mapper;

import org.fleetguard.dto.TripDto;
import org.fleetguard.dto.UserRefDto;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TripMapper {

    public TripDto toDto(Trip trip) {
        return new TripDto(
                trip.getId().toString(),
                trip.getVehicle().getId().toString(),
                trip.getStatus() == TripStatus.OPEN ? "open" : "closed",
                trip.getStartedAt() == null ? null : trip.getStartedAt().toString(),
                trip.getEndedAt() == null ? null : trip.getEndedAt().toString(),
                userRef(trip.getOpenedById(), trip.getOpenedByName()),
                userRef(trip.getClosedById(), trip.getClosedByName())
        );
    }

    private static UserRefDto userRef(UUID id, String name) {
        return id == null ? null : new UserRefDto(id.toString(), name);
    }
}
