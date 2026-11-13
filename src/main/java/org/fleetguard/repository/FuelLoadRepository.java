package org.fleetguard.repository;

import org.fleetguard.entity.FuelLoad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FuelLoadRepository extends JpaRepository<FuelLoad, UUID> {

    /** Las cargas de un vehículo en el orden en que se hicieron (CAM-156). */
    List<FuelLoad> findByVehicle_IdOrderByLoadedAtAscOdometerKmAsc(UUID vehicleId);

    /** Las cargas del período [from, to) con su vehículo, para Gastos. */
    @Query("select f from FuelLoad f join fetch f.vehicle where f.loadedAt >= :from and f.loadedAt < :to")
    List<FuelLoad> findInRangeWithVehicle(@Param("from") Instant from, @Param("to") Instant to);
}
