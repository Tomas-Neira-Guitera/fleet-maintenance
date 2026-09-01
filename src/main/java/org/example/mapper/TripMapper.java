package org.example.mapper;

import org.example.dto.TripDto;
import org.example.entity.Trip;
import org.example.entity.TripStatus;
import org.springframework.stereotype.Component;

@Component
public class TripMapper {

    public TripDto toDto(Trip trip) {
        return new TripDto(
                trip.getId().toString(),
                trip.getVehicle().getId().toString(),
                trip.getStatus() == TripStatus.OPEN ? "open" : "closed",
                trip.getStartedAt() == null ? null : trip.getStartedAt().toString(),
                trip.getEndedAt() == null ? null : trip.getEndedAt().toString()
        );
    }
}
