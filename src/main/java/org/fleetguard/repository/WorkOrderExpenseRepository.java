package org.fleetguard.repository;

import org.fleetguard.entity.WorkOrderExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkOrderExpenseRepository extends JpaRepository<WorkOrderExpense, UUID> {

    List<WorkOrderExpense> findByWorkOrder_IdOrderByCreatedAtAsc(UUID workOrderId);

    /** Gastos de todas las OTs cargados en [from, to), con su OT, del más reciente al más antiguo -- CAM-52. */
    @Query("select e from WorkOrderExpense e join fetch e.workOrder w "
            + "where e.createdAt >= :from and e.createdAt < :to order by e.createdAt desc")
    List<WorkOrderExpense> findInRangeWithWorkOrder(@Param("from") Instant from, @Param("to") Instant to);
}
