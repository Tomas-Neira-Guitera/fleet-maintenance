package org.fleetguard.repository;

import org.fleetguard.entity.WorkOrderComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkOrderCommentRepository extends JpaRepository<WorkOrderComment, UUID> {

    /** CAM-149: los comentarios de una OT, del más viejo al más nuevo. */
    List<WorkOrderComment> findByWorkOrder_IdOrderByCreatedAtAsc(UUID workOrderId);
}
