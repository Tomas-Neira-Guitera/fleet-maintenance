package org.fleetguard.repository;

import org.fleetguard.entity.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    List<Inspection> findByVehicleIdOrderByTimestampDesc(UUID vehicleId);

    /** Inspecciones hechas en [from, to), por chofer -- CAM-91. */
    @Query("select new org.fleetguard.repository.DriverCount(i.driverId, count(i)) from Inspection i "
            + "where i.timestamp >= :from and i.timestamp < :to group by i.driverId")
    List<DriverCount> countByDriver(@Param("from") Instant from, @Param("to") Instant to);
}
