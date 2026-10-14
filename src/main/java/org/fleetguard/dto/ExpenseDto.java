package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Un gasto de mantenimiento con el vehículo y la OT de la que sale -- GET /api/expenses (CAM-52).
 * origin: "plan" (preventivo), "defect" (correctivo) o "manual", según de dónde nació la OT.
 */
public record ExpenseDto(
        String id,
        String date,
        String vehicleId,
        String plate,
        String category,
        String description,
        BigDecimal amount,
        String workOrderId,
        String workOrderTitle,
        String workOrderStatus,
        String origin,
        String executionType,
        /** Número del comprobante (CAM-107); null si no se cargó. */
        String receiptNumber,
        /** Foto del comprobante (CAM-107); null si no se adjuntó. */
        String receiptPhotoUrl
) {
}
