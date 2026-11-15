package org.fleetguard.repository;

import org.fleetguard.entity.RecurringDefectAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface RecurringDefectAlertRepository extends JpaRepository<RecurringDefectAlert, UUID> {

    /**
     * CAM-135: si ya se avisó ese control de ese vehículo desde {@code since}. CAM-144: itemIds trae todos los
     * ids del control, porque las marcas anteriores pueden tener el id del post-viaje.
     */
    boolean existsByVehicleIdAndItemIdInAndSentAtAfter(UUID vehicleId, Collection<String> itemIds, Instant since);
}
