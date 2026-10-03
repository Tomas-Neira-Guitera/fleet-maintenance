package org.fleetguard.repository;

import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    List<WorkOrder> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);

    List<WorkOrder> findAllByOrderByCreatedAtDesc();

    // OT abierta (asignada/en proceso) para un mismo origen -- evitan duplicar trabajo al replanificar.
    // Ordenadas por fecha para que, si ya hay duplicados viejos en la base, siempre se reuse la original.

    Optional<WorkOrder> findFirstByDefectIdAndStatusInOrderByCreatedAtAsc(UUID defectId,
                                                                         Collection<WorkOrderStatus> statuses);

    Optional<WorkOrder> findFirstByAssignmentIdAndStatusInOrderByCreatedAtAsc(UUID assignmentId,
                                                                             Collection<WorkOrderStatus> statuses);

    Optional<WorkOrder> findFirstByScheduledMaintenanceIdAndStatusInOrderByCreatedAtAsc(UUID scheduledMaintenanceId,
                                                                                       Collection<WorkOrderStatus> statuses);

    List<WorkOrder> findByScheduledMaintenanceIdAndStatusIn(UUID scheduledMaintenanceId,
                                                             Collection<WorkOrderStatus> statuses);

    // CAM-23: un técnico con OTs abiertas no puede cambiar de rol hasta que se las reasignen.
    long countByTechnicianIdAndStatusIn(UUID technicianId, Collection<WorkOrderStatus> statuses);
}
