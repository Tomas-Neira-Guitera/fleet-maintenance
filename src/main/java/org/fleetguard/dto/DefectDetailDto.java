package org.fleetguard.dto;

/**
 * Coincide con components.schemas.DefectDetail de openapi.yaml. description es el título corto
 * del defecto (CAM-79); details es la descripción larga, opcional, que se puede dictar (CAM-32).
 */
public record DefectDetailDto(String severity, String description, String photoUrl, String details) {
    public DefectDetailDto(String severity, String description, String photoUrl) {
        this(severity, description, photoUrl, null);
    }
}
