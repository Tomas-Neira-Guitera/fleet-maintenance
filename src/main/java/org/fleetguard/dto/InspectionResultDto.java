package org.fleetguard.dto;


/**
 * Coincide con components.schemas.InspectionResult de openapi.yaml -- body de la respuesta 201. trailerInspection:
 * la del remolque revisado en el mismo pre-viaje (CAM-159), o null.
 */
public record InspectionResultDto(InspectionDto inspection, TripDto trip, InspectionDto trailerInspection) {

    public InspectionResultDto(InspectionDto inspection, TripDto trip) {
        this(inspection, trip, null);
    }
}
