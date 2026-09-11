package org.example.repository;

import org.example.entity.ScheduleStatus;
import org.example.entity.ScheduledMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledMaintenanceRepository extends JpaRepository<ScheduledMaintenance, UUID> {

    Optional<ScheduledMaintenance> findFirstByAssignmentIdAndStatus(UUID assignmentId, ScheduleStatus status);

    Optional<ScheduledMaintenance> findFirstByDefectIdAndStatus(UUID defectId, ScheduleStatus status);

    List<ScheduledMaintenance> findByScheduledAtBetweenAndStatusOrderByScheduledAtAsc(Instant from, Instant to, ScheduleStatus status);

    /** Filtro por vehículo -- alimenta el preview de "ya programado para este vehículo" (CAM-42 mejoras). */
    List<ScheduledMaintenance> findByVehicleIdAndScheduledAtBetweenAndStatusOrderByScheduledAtAsc(
            UUID vehicleId, Instant from, Instant to, ScheduleStatus status);
}
