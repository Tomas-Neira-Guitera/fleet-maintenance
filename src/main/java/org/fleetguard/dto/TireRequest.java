package org.fleetguard.dto;

import java.math.BigDecimal;

/** Body de POST y PUT /api/tires (CAM-157). code y model son opcionales. */
public record TireRequest(String code, String brand, String model, String size, String purchaseDate, BigDecimal cost) {
}
