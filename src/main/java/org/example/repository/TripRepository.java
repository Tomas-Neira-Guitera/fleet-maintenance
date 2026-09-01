package org.example.repository;

import org.example.entity.Trip;
import org.example.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findFirstByVehicle_IdAndStatus(UUID vehicleId, TripStatus status);
}
