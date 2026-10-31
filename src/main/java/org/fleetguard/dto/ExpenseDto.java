package org.fleetguard.dto;

import java.math.BigDecimal;

/**
 * Un gasto con su vehículo y, si sale de una, su OT -- GET /api/expenses (CAM-52, CAM-109).
 * origin: "plan" (preventivo), "defect" (correctivo) o "manual", según de dónde nació la OT, u
 * "operating" si es un gasto operativo: ahí los campos de la OT, executionType y el comprobante van en null.
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
