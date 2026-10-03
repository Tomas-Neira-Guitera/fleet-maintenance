package org.fleetguard.repository;

import org.fleetguard.entity.VehicleChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleChecklistItemRepository extends JpaRepository<VehicleChecklistItem, UUID> {

    List<VehicleChecklistItem> findByVehicleIdAndActiveTrueOrderByCreatedAtAsc(UUID vehicleId);

    List<VehicleChecklistItem> findByVehicleIdOrderByCreatedAtAsc(UUID vehicleId);
}
