package org.fleetguard.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Los km registrados de un vehículo y cuándo (CAM-137), tal como salen de la base. Un record por
 * fuente, cada uno con un solo constructor: con varios, Hibernate elige el primero que encuentra
 * y no el que coincide con los tipos (falla con "Cannot cast Double to Long").
 */
public final class KmReadingRow {

    private KmReadingRow() {
    }

    /** De una inspección: el km viene con decimales. */
    public record FromInspection(UUID vehicleId, Instant timestamp, Double odometerKm) {
    }

    /** De un mantenimiento hecho: solo guarda el día. */
    public record FromCompletion(UUID vehicleId, LocalDate completedAt, Long completedKm) {
    }
}
