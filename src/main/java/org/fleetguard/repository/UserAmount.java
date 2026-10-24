package org.fleetguard.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Fila de una consulta agrupada por usuario con un monto (gasto cargado por técnico) -- CAM-91. */
public record UserAmount(UUID userId, BigDecimal amount) {
}
