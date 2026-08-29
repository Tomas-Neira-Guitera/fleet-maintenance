package org.example.dto;

/** Matches components.schemas.DefectDetail in openapi.yaml. */
public record DefectDetailDto(String severity, String description, String photoUrl) {
}
