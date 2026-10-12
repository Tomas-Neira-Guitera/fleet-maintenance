package org.fleetguard.repository;

import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** OTs en cada estado pedido, por técnico a cargo -- CAM-91. */
    @Query("select new org.fleetguard.repository.UserCount(w.technicianId, count(w)) from WorkOrder w "
            + "where w.technicianId is not null and w.status in :statuses group by w.technicianId")
    List<UserCount> countByTechnician(@Param("statuses") Collection<WorkOrderStatus> statuses);

    /** OTs con técnico finalizadas en [from, to) -- CAM-91. */
    @Query("select w from WorkOrder w where w.technicianId is not null "
            + "and w.status = org.fleetguard.entity.WorkOrderStatus.FINALIZADA "
            + "and w.finalizedAt >= :from and w.finalizedAt < :to")
    List<WorkOrder> findFinalizedWithTechnician(@Param("from") Instant from, @Param("to") Instant to);
}
