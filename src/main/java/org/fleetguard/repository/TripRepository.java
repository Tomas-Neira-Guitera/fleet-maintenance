package org.fleetguard.repository;

import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findFirstByVehicle_IdAndStatus(UUID vehicleId, TripStatus status);

    /** Todos los viajes abiertos, con su vehículo: arma el listado de flota sin una consulta por vehículo. */
    @Query("select t from Trip t join fetch t.vehicle where t.status = :status")
    List<Trip> findByStatusWithVehicle(@Param("status") TripStatus status);

    /**
     * Viajes iniciados en el período, por chofer que los abrió (CAM-91). No cuenta los que se
     * cerraron en el mismo instante: son pre-viajes con defecto bloqueante (CAM-86), no viajes.
     */
    @Query("select new org.fleetguard.repository.UserCount(t.openedById, count(t)) from Trip t "
            + "where t.openedById is not null and t.startedAt >= :from and t.startedAt < :to "
            + "and (t.endedAt is null or t.endedAt <> t.startedAt) group by t.openedById")
    List<UserCount> countTripsByDriver(@Param("from") Instant from, @Param("to") Instant to);
}
