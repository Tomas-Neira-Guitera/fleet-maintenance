package org.example.dto;

/** Coincide con components.schemas.DefectDetail de openapi.yaml. */
public record DefectDetailDto(String severity, String description, String photoUrl) {
}
