package org.fleetguard.dto;

import java.util.List;

/** Coincide con components.schemas.InspectionSubmission de openapi.yaml -- body de POST /api/inspections/{vehicleId}. */
public record InspectionSubmissionDto(
        String type,
        List<ChecklistAnswerDto> answers,
        String notes,
        // CAM-153: cuándo la hizo el chofer (ISO-8601). Sin conexión llega más tarde; null = ahora.
        String performedAt,
        // CAM-153: UUID que arma el celular. Si llega dos veces, la segunda devuelve la ya guardada.
        String clientSubmissionId,
        // CAM-157: opcional, solo en el pre-viaje. Dibujo de las cubiertas montadas, por posición.
        List<TreadReadingInput> treadReadings
) {
    public InspectionSubmissionDto(String type, List<ChecklistAnswerDto> answers, String notes) {
        this(type, answers, notes, null, null, null);
    }

    public InspectionSubmissionDto(String type, List<ChecklistAnswerDto> answers, String notes, String performedAt,
                                   String clientSubmissionId) {
        this(type, answers, notes, performedAt, clientSubmissionId, null);
    }
}
