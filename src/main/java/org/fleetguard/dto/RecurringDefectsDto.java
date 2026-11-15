package org.fleetguard.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/defects/recurring (CAM-135): los controles que fallan seguido, con la regla que se aplicó. */
public record RecurringDefectsDto(int threshold, int windowDays, List<Item> items) {

    /** Un control de un vehículo que llegó al umbral dentro de la ventana. */
    public record Item(String vehicleId, String plate, String itemId, String itemLabel, int count,
                       Instant lastReportedAt, List<Occurrence> occurrences) {
    }

    /** Cada falla de la ventana. */
    public record Occurrence(String defectId, Instant reportedAt, String severity, String status,
                             String description) {
    }
}
