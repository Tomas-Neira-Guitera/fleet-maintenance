package org.fleetguard.dto;

import java.math.BigDecimal;

public record WorkOrderExpenseDto(
        String id,
        String category,
        String description,
        BigDecimal amount,
        String createdAt,
        /** Número del comprobante (CAM-107); null si no se cargó. */
        String receiptNumber,
        /** Foto del comprobante (CAM-107); null si no se adjuntó. */
        String receiptPhotoUrl
) {
}
