package org.fleetguard.repository;

import org.fleetguard.entity.ScheduledMaintenanceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ScheduledMaintenanceAlertRepository extends JpaRepository<ScheduledMaintenanceAlert, UUID> {

    /** CAM-143: si ese turno ya se avisó para esa fecha. */
    boolean existsByScheduleIdAndScheduledAt(UUID scheduleId, Instant scheduledAt);
}
