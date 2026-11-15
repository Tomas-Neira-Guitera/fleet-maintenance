package org.fleetguard.repository;

import org.fleetguard.entity.VehicleChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleChecklistItemRepository extends JpaRepository<VehicleChecklistItem, UUID> {

    List<VehicleChecklistItem> findByVehicleIdAndActiveTrueOrderByCreatedAtAsc(UUID vehicleId);

    List<VehicleChecklistItem> findByVehicleIdOrderByCreatedAtAsc(UUID vehicleId);

    // CAM-159: los controles copiados de una plantilla, en toda la flota o en un vehículo.
    List<VehicleChecklistItem> findByTemplateId(UUID templateId);

    List<VehicleChecklistItem> findByVehicleIdAndTemplateId(UUID vehicleId, UUID templateId);
}
