package org.fleetguard.repository;

import org.fleetguard.entity.VehicleDisabledChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDisabledChecklistItemRepository extends JpaRepository<VehicleDisabledChecklistItem, UUID> {

    List<VehicleDisabledChecklistItem> findByVehicleId(UUID vehicleId);

    Optional<VehicleDisabledChecklistItem> findByVehicleIdAndBaseItemId(UUID vehicleId, String baseItemId);
}
