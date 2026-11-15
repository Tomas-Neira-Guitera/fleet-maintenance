package org.fleetguard.dto;

/**
 * Por qué un vehículo está "No disponible" (CAM-127). type: "blocking_defect" o "expired_document";
 * message ya armado para mostrar (por ejemplo "VTV / RTO: venció el 12/10/2026").
 */
public record BlockReasonDto(String type, String message) {

    public static final String BLOCKING_DEFECT = "blocking_defect";
    public static final String EXPIRED_DOCUMENT = "expired_document";
}
