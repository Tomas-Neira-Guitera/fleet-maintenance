package org.fleetguard.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// CAM-157: el texto del aviso de neumáticos con problemas.
class TireProblemReportedListenerTest {

    @Test
    void elAvisoDiceQuienReportoYQueNeumaticos() {
        TireProblemReportedEvent event = new TireProblemReportedEvent(UUID.randomUUID(), "AB123CD", "chofer1",
                List.of("Trasera derecha exterior (Michelin X Multi N° 1234): se ve la tela o el alambre",
                        "Delantera izquierda (Fate Sentiva): pinchado"));

        assertEquals("Neumáticos con problemas en AB123CD", TireProblemReportedListener.title(event));
        assertEquals("chofer1 reportó en AB123CD: Trasera derecha exterior (Michelin X Multi N° 1234): se ve la tela o "
                + "el alambre; Delantera izquierda (Fate Sentiva): pinchado.", TireProblemReportedListener.message(event));
    }

    @Test
    void conUnSoloNeumaticoElTituloVaEnSingular() {
        TireProblemReportedEvent event = new TireProblemReportedEvent(UUID.randomUUID(), "AB123CD", null,
                List.of("Auxilio (Fate): baja presión o desinflado"));

        assertEquals("Neumático con problemas en AB123CD", TireProblemReportedListener.title(event));
        assertEquals("Se reportó en AB123CD: Auxilio (Fate): baja presión o desinflado.",
                TireProblemReportedListener.message(event));
    }
}
