package org.fleetguard.dto;

import java.util.List;

/** Body de POST /api/vehicles/{id}/tire-treads (CAM-157): el admin carga mediciones del dibujo. */
public record TreadReadingsRequest(List<TreadReadingInput> readings) {
}
