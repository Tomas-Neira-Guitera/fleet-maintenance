package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Body de POST /api/work-orders/{id}/expenses. El comprobante es opcional (CAM-107): receiptNumber,
 * hasta 50 caracteres, y receiptPhotoUrl, que sale de POST /api/photos.
 */
public record CreateWorkOrderExpenseRequest(
        String category,
        String description,
        BigDecimal amount,
        String receiptNumber,
        String receiptPhotoUrl
) {
}
