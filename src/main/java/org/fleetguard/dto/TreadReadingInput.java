package org.fleetguard.dto;

import java.math.BigDecimal;

/** Una medición del dibujo (CAM-157), en el pre-viaje o cargada por el admin. depthMm entre 0 y 30, un decimal. */
public record TreadReadingInput(String position, BigDecimal depthMm) {
}
