package org.fleetguard.repository;

import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    List<Inspection> findByVehicleIdOrderByTimestampDesc(UUID vehicleId);

    /** Igual, con el viaje de cada inspección, para el historial (CAM-22, CAM-152) sin una consulta por viaje. */
    @Query("select i from Inspection i join fetch i.trip where i.vehicleId = :vehicleId order by i.timestamp desc")
    List<Inspection> findByVehicleIdWithTripOrderByTimestampDesc(@Param("vehicleId") UUID vehicleId);

    /** Inspecciones hechas en [from, to), por chofer -- CAM-91. */
    @Query("select new org.fleetguard.repository.DriverCount(i.driverId, count(i)) from Inspection i "
            + "where i.timestamp >= :from and i.timestamp < :to group by i.driverId")
    List<DriverCount> countByDriver(@Param("from") Instant from, @Param("to") Instant to);

    /** Los km cargados en las inspecciones de varios vehículos, de toda su historia -- CAM-137. */
    @Query("select new org.fleetguard.repository.KmReadingRow$FromInspection(i.vehicleId, i.timestamp, i.odometerKm) "
            + "from Inspection i "
            + "where i.vehicleId in :vehicleIds and i.odometerKm is not null and i.timestamp is not null")
    List<KmReadingRow.FromInspection> findKmReadings(@Param("vehicleIds") Collection<UUID> vehicleIds);

    /**
     * Los km de las inspecciones de los viajes cerrados de un vehículo, salvo el de {@code exceptTripId}
     * -- CAM-152. Los viajes que se cierran en el mismo instante en que se abren (pre-viaje con un
     * defecto bloqueante, CAM-86) no tienen post-viaje y quedan solos con su pre-viaje.
     */
    @Query("select new org.fleetguard.repository.TripInspectionKmRow(t.id, t.endedAt, i.type, i.odometerKm) "
            + "from Inspection i join i.trip t "
            + "where i.vehicleId = :vehicleId and t.status = org.fleetguard.entity.TripStatus.CLOSED "
            + "and t.id <> :exceptTripId and i.odometerKm is not null")
    List<TripInspectionKmRow> findClosedTripKm(@Param("vehicleId") UUID vehicleId,
                                               @Param("exceptTripId") UUID exceptTripId);

    /** El pre-viaje de un viaje -- CAM-152. */
    Optional<Inspection> findFirstByTrip_IdAndType(UUID tripId, InspectionType type);
}
