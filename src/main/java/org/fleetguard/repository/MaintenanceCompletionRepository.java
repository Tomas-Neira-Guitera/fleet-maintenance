package org.fleetguard.repository;

import org.fleetguard.entity.MaintenanceCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceCompletionRepository extends JpaRepository<MaintenanceCompletion, UUID> {

    List<MaintenanceCompletion> findByAssignment_IdOrderByCompletedAtDesc(UUID assignmentId);

    Optional<MaintenanceCompletion> findFirstByAssignment_IdOrderByCompletedAtDesc(UUID assignmentId);

    /** Todas las completions de un vehículo (CAM-22), con el plan ya cargado para no hacer N+1. */
    @Query("select c from MaintenanceCompletion c join fetch c.assignment a join fetch a.maintenancePlan "
            + "where a.vehicleId = :vehicleId order by c.completedAt desc")
    List<MaintenanceCompletion> findByVehicleIdWithPlan(@Param("vehicleId") UUID vehicleId);

    /** Los km con que se hicieron los mantenimientos de varios vehículos, de toda su historia -- CAM-137. */
    @Query("select new org.fleetguard.repository.KmReadingRow$FromCompletion(a.vehicleId, c.completedAt, c.completedKm) "
            + "from MaintenanceCompletion c join c.assignment a "
            + "where a.vehicleId in :vehicleIds and c.completedKm is not null")
    List<KmReadingRow.FromCompletion> findKmReadings(@Param("vehicleIds") Collection<UUID> vehicleIds);
}
