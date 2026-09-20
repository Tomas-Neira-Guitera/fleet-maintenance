package org.example.repository;

import org.example.entity.WorkOrderPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkOrderPhotoRepository extends JpaRepository<WorkOrderPhoto, UUID> {

    List<WorkOrderPhoto> findByWorkOrder_IdOrderByCreatedAtAsc(UUID workOrderId);

    long countByWorkOrder_Id(UUID workOrderId);
}
