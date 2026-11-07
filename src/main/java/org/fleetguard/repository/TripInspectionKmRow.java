package org.fleetguard.repository;

import org.fleetguard.entity.InspectionType;

import java.time.Instant;
import java.util.UUID;

/**
 * El km de una inspección de un viaje cerrado (CAM-152): con el del pre-viaje y el del post-viaje se
 * saca cuánto recorrió. Un solo constructor: con varios, Hibernate puede elegir el que no es.
 */
public record TripInspectionKmRow(UUID tripId, Instant tripEndedAt, InspectionType type, Double odometerKm) {
}
