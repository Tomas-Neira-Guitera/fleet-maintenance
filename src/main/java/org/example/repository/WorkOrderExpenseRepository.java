package org.example.repository;

import org.example.entity.WorkOrderExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkOrderExpenseRepository extends JpaRepository<WorkOrderExpense, UUID> {

    List<WorkOrderExpense> findByWorkOrder_IdOrderByCreatedAtAsc(UUID workOrderId);
}
