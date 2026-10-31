package org.fleetguard.repository;

import org.fleetguard.entity.RecurringDefectAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface RecurringDefectAlertRepository extends JpaRepository<RecurringDefectAlert, UUID> {

    /** CAM-135: si ya se avisó ese control de ese vehículo desde {@code since}. */
    boolean existsByVehicleIdAndItemIdAndSentAtAfter(UUID vehicleId, String itemId, Instant since);
}
