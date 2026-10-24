package org.fleetguard.service;

import org.fleetguard.entity.PushSubscription;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAM-119 -- la push se prende solo con un par de claves VAPID válido; sin él, la app levanta igual. */
class WebPushGatewayTest {

    // El par de desarrollo de application.yml.
    private static final String PUBLIC_KEY =
            "BKxlwImnwTE3lIxq80BPqCK_OXHCr99VaymbSmhSWg_oiAdaZpNE9xDxYWnAILceWOQV8P3gd1ZyGdFq5wjZYfk";
    private static final String PRIVATE_KEY = "9M6iS3GP2Kf691Lx1pzJLaoslLYgCAETR2hq9dnzQ8g";

    @Test
    void conClavesValidasQuedaPrendida() {
        WebPushGateway gateway = new WebPushGateway(PUBLIC_KEY, PRIVATE_KEY, "mailto:test@example.com");

        assertTrue(gateway.isEnabled());
        assertEquals(PUBLIC_KEY, gateway.publicKey());
    }

    @Test
    void sinClavesOConClavesInvalidasQuedaApagadaYNoManda() {
        WebPushGateway empty = new WebPushGateway("", "", "mailto:test@example.com");
        assertFalse(empty.isEnabled());
        assertNull(empty.publicKey());
        assertThrows(IllegalStateException.class, () -> empty.send(
                new PushSubscription(UUID.randomUUID(), "https://fcm.googleapis.com/x", "p", "a"), "{}"));

        WebPushGateway invalid = new WebPushGateway("no-es-una-clave", "tampoco", "mailto:test@example.com");
        assertFalse(invalid.isEnabled());
        assertNull(invalid.publicKey());
    }
}
