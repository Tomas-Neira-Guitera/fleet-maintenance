package org.fleetguard.repository;

import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findFirstByVehicle_IdAndStatus(UUID vehicleId, TripStatus status);

    /** Todos los viajes abiertos, con su vehículo: arma el listado de flota sin una consulta por vehículo. */
    @Query("select t from Trip t join fetch t.vehicle where t.status = :status")
    List<Trip> findByStatusWithVehicle(@Param("status") TripStatus status);
}
