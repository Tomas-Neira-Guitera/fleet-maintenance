package org.fleetguard.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Lectura tolerante de ids que llegan en el path. Un id mal formado ("abc") tiene que terminar
 * en el mismo 404 que un id que no existe: con UUID.fromString pelado, la
 * IllegalArgumentException caía en el handler genérico y salía como 500.
 */
final class Uuids {

    private Uuids() {
    }

    static Optional<UUID> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
