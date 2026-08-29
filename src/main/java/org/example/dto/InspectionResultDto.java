package org.example.dto;


/** Matches components.schemas.InspectionResult in openapi.yaml -- the 201 response body. */
public record InspectionResultDto(InspectionDto inspection, TripDto trip) {
}
