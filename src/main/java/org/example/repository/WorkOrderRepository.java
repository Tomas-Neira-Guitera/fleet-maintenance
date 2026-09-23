package org.example.repository;

import org.example.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    List<WorkOrder> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);

    List<WorkOrder> findAllByOrderByCreatedAtDesc();
}
