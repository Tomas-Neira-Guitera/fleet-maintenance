package org.fleetguard.repository;

import java.util.UUID;

/** Fila de una consulta agrupada por usuario: cuántos viajes, OTs, etc. tiene cada uno -- CAM-91. */
public record UserCount(UUID userId, long count) {
}
