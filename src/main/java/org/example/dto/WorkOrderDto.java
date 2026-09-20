package org.example.dto;

import java.math.BigDecimal;
import java.util.List;

/** Coincide con GET/POST/PATCH /api/work-orders(/{id}) -- ver claude/CAM-14-ordenes-de-trabajo.md. */
public record WorkOrderDto(
        String id,
        String vehicleId,
        String plate,
        String sourceType,
        String scheduledMaintenanceId,
        String defectId,
        String assignmentId,
        String title,
        String description,
        String executionType,
        String externalProvider,
        String assignee,
        String status,
        String closingDescription,
        List<WorkOrderExpenseDto> expenses,
        BigDecimal totalExpenses,
        List<WorkOrderPhotoDto> photos,
        String createdAt,
        String updatedAt,
        String finalizedAt
) {
}
