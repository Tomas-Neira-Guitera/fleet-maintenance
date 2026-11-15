package org.fleetguard.dto;

import java.math.BigDecimal;

/** Body de POST y PUT /api/expenses (CAM-109). date es un día, YYYY-MM-DD. */
public record VehicleExpenseRequest(
        String vehicleId,
        String date,
        String category,
        String description,
        BigDecimal amount
) {
}
