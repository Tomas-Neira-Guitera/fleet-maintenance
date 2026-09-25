package org.example.dto;

import java.math.BigDecimal;

/** Body de POST /api/work-orders/{id}/expenses. */
public record CreateWorkOrderExpenseRequest(
        String category,
        String description,
        BigDecimal amount
) {
}
