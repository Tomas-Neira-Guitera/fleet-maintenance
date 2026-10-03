package org.fleetguard.dto;

import java.math.BigDecimal;

public record WorkOrderExpenseDto(
        String id,
        String category,
        String description,
        BigDecimal amount,
        String createdAt
) {
}
