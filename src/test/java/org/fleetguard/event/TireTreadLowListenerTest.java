package org.fleetguard.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// CAM-157: el texto del aviso de dibujo bajo.
class TireTreadLowListenerTest {

    @Test
    void elAvisoDiceQueCubiertaEsDondeEstaYCuantoDibujoTiene() {
        TireTreadLowEvent event = new TireTreadLowEvent(UUID.randomUUID(), "AB123CD", "Michelin X Multi N° 1234",
                "Delantera izquierda", new BigDecimal("1.4"), new BigDecimal("1.6"), "chofer1");

        assertEquals("La cubierta Michelin X Multi N° 1234 (Delantera izquierda) de AB123CD tiene 1,4 mm de dibujo, "
                + "menos del mínimo de 1,6 mm. Lo midió chofer1.", TireTreadLowListener.message(event));
    }
}
