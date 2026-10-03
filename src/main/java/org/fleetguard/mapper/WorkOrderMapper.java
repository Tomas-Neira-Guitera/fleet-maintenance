package org.fleetguard.mapper;

import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.WorkOrderDto;
import org.fleetguard.dto.WorkOrderExpenseDto;
import org.fleetguard.dto.WorkOrderPhotoDto;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.entity.WorkOrderPhoto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class WorkOrderMapper {

    public WorkOrderDto toDto(WorkOrder workOrder, String plate, String technicianUsername, DefectDto defect,
                              List<WorkOrderExpense> expenses, List<WorkOrderPhoto> photos) {
        List<WorkOrderExpenseDto> expenseDtos = expenses.stream().map(this::toExpenseDto).toList();
        List<WorkOrderPhotoDto> photoDtos = photos.stream().map(this::toPhotoDto).toList();
        BigDecimal total = expenses.stream().map(WorkOrderExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WorkOrderDto(
                workOrder.getId().toString(),
                workOrder.getVehicleId().toString(),
                plate,
                workOrder.getSourceType().toJson(),
                workOrder.getScheduledMaintenanceId() == null ? null : workOrder.getScheduledMaintenanceId().toString(),
                workOrder.getDefectId() == null ? null : workOrder.getDefectId().toString(),
                defect,
                workOrder.getAssignmentId() == null ? null : workOrder.getAssignmentId().toString(),
                workOrder.getTitle(),
                workOrder.getDescription(),
                workOrder.getExecutionType().toJson(),
                workOrder.getExternalProvider(),
                workOrder.getAssignee(),
                workOrder.getTechnicianId() == null ? null : workOrder.getTechnicianId().toString(),
                technicianUsername,
                workOrder.getStatus().toJson(),
                workOrder.getClosingDescription(),
                expenseDtos,
                total,
                photoDtos,
                workOrder.getCreatedAt().toString(),
                workOrder.getUpdatedAt().toString(),
                workOrder.getFinalizedAt() == null ? null : workOrder.getFinalizedAt().toString()
        );
    }

    public WorkOrderExpenseDto toExpenseDto(WorkOrderExpense expense) {
        return new WorkOrderExpenseDto(
                expense.getId().toString(),
                expense.getCategory().toJson(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCreatedAt().toString()
        );
    }

    public WorkOrderPhotoDto toPhotoDto(WorkOrderPhoto photo) {
        return new WorkOrderPhotoDto(
                photo.getId().toString(),
                photo.getPhotoUrl(),
                photo.getCreatedAt().toString()
        );
    }
}
