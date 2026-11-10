package org.fleetguard.dto;

import java.util.List;

/** GET /api/tires/{id} (CAM-157): la cubierta, dónde estuvo y sus mediciones de dibujo. */
public record TireDetailDto(TireDto tire, List<TireMountingDto> mountings, List<TireDto.Tread> treads) {
}
