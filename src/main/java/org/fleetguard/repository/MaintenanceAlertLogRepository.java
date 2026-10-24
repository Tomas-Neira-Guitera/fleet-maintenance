package org.fleetguard.repository;

import org.fleetguard.entity.MaintenanceAlertLog;
import org.fleetguard.entity.MaintenanceAlertStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenanceAlertLogRepository extends JpaRepository<MaintenanceAlertLog, UUID> {

    boolean existsByAssignmentIdAndStageAndCycleKey(UUID assignmentId, MaintenanceAlertStage stage, String cycleKey);
}
