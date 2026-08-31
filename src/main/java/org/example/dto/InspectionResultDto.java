package org.example.dto;


/** Coincide con components.schemas.InspectionResult de openapi.yaml -- body de la respuesta 201. */
public record InspectionResultDto(InspectionDto inspection, TripDto trip) {
}
