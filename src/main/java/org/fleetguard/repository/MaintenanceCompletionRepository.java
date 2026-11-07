package org.fleetguard.repository;

import org.fleetguard.entity.MaintenanceCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceCompletionRepository extends JpaRepository<MaintenanceCompletion, UUID> {

    List<MaintenanceCompletion> findByAssignment_IdOrderByCompletedAtDesc(UUID assignmentId);

    Optional<MaintenanceCompletion> findFirstByAssignment_IdOrderByCompletedAtDesc(UUID assignmentId);

    /** CAM-155: las completions hechas antes de esa fecha, con su plan (para saber cuándo vencía cada una). */
    @Query("select c from MaintenanceCompletion c join fetch c.assignment a join fetch a.maintenancePlan "
            + "where c.completedAt < :before")
    List<MaintenanceCompletion> findCompletedBeforeWithPlan(@Param("before") LocalDate before);

    /** Todas las completions de un vehículo (CAM-22), con el plan ya cargado para no hacer N+1. */
    @Query("select c from MaintenanceCompletion c join fetch c.assignment a join fetch a.maintenancePlan "
            + "where a.vehicleId = :vehicleId order by c.completedAt desc")
    List<MaintenanceCompletion> findByVehicleIdWithPlan(@Param("vehicleId") UUID vehicleId);
}
