package org.fleetguard.repository;

import org.fleetguard.entity.TireMounting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TireMountingRepository extends JpaRepository<TireMounting, UUID> {

    /** Todos los montajes con su cubierta y su vehículo, para sumar km (CAM-157). */
    @Query("select m from TireMounting m join fetch m.tire join fetch m.vehicle order by m.mountedAt asc")
    List<TireMounting> findAllWithTireAndVehicle();

    List<TireMounting> findByTire_IdOrderByMountedAtAsc(UUID tireId);

    List<TireMounting> findByVehicle_IdOrderByMountedAtDesc(UUID vehicleId);

    List<TireMounting> findByVehicle_IdAndRemovedAtIsNull(UUID vehicleId);

    Optional<TireMounting> findFirstByTire_IdAndRemovedAtIsNull(UUID tireId);

    boolean existsByTire_Id(UUID tireId);
}
