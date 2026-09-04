package org.example.repository;

import org.example.entity.VehicleMaintenanceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleMaintenanceAssignmentRepository extends JpaRepository<VehicleMaintenanceAssignment, UUID> {

    List<VehicleMaintenanceAssignment> findByVehicleId(UUID vehicleId);

    List<VehicleMaintenanceAssignment> findByVehicleIdAndActive(UUID vehicleId, boolean active);

    List<VehicleMaintenanceAssignment> findByVehicleIdIn(List<UUID> vehicleIds);

    Optional<VehicleMaintenanceAssignment> findByIdAndVehicleId(UUID id, UUID vehicleId);

    Optional<VehicleMaintenanceAssignment> findFirstByVehicleIdAndMaintenancePlan_IdAndActiveTrue(UUID vehicleId, UUID maintenancePlanId);

    Optional<VehicleMaintenanceAssignment> findFirstByVehicleIdAndMaintenancePlan_IdAndActiveFalse(UUID vehicleId, UUID maintenancePlanId);

    boolean existsByMaintenancePlan_Id(UUID maintenancePlanId);
}
