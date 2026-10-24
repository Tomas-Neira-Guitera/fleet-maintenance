package org.fleetguard.repository;

import org.fleetguard.entity.ScheduleStatus;
import org.fleetguard.entity.ScheduledMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledMaintenanceRepository extends JpaRepository<ScheduledMaintenance, UUID> {

    Optional<ScheduledMaintenance> findFirstByAssignmentIdAndStatus(UUID assignmentId, ScheduleStatus status);

    Optional<ScheduledMaintenance> findFirstByDefectIdAndStatus(UUID defectId, ScheduleStatus status);

    /** Varios estados a la vez: el calendario (CAM-65) pide programadas, hechas y canceladas en una sola llamada. */
    List<ScheduledMaintenance> findByScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(
            Instant from, Instant to, Collection<ScheduleStatus> statuses);

    /** Filtro por vehículo -- alimenta el preview de "ya programado para este vehículo" (CAM-42 mejoras). */
    List<ScheduledMaintenance> findByVehicleIdAndScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(
            UUID vehicleId, Instant from, Instant to, Collection<ScheduleStatus> statuses);
}
