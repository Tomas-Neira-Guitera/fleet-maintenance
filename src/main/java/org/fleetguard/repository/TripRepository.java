package org.fleetguard.repository;

import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findFirstByVehicle_IdAndStatus(UUID vehicleId, TripStatus status);
}
