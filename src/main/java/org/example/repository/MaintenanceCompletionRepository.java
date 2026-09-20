package org.example.repository;

import org.example.entity.MaintenanceCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
