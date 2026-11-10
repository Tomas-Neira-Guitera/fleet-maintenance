package org.fleetguard.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// CAM-157: las posiciones de cada configuración de ejes y cómo se llaman.
class TireLayoutTest {

    @Test
    void cadaConfiguracionTieneSusPosicionesYAuxilio() {
        assertEquals(5, TireLayout.CUATRO_RUEDAS.positions().size());
        assertEquals(7, TireLayout.SEIS_RUEDAS.positions().size());
        assertEquals(11, TireLayout.DIEZ_RUEDAS.positions().size());
        for (TireLayout layout : TireLayout.values()) {
            assertTrue(layout.has(TirePosition.AUXILIO));
            assertEquals(layout, TireLayout.fromJson(layout.toJson()));
        }
        assertFalse(TireLayout.CUATRO_RUEDAS.has(TirePosition.E2_IZQ_EXT));
    }

    @Test
    void elNombreDependeDeLaConfiguracion() {
        assertEquals("Trasera izquierda", TireLayout.CUATRO_RUEDAS.labelOf(TirePosition.TRAS_IZQ));
        assertEquals("Trasera izquierda exterior", TireLayout.SEIS_RUEDAS.labelOf(TirePosition.E2_IZQ_EXT));
        assertEquals("Eje 3 derecha interior", TireLayout.DIEZ_RUEDAS.labelOf(TirePosition.E3_DER_INT));
        assertEquals("Auxilio", TireLayout.DIEZ_RUEDAS.labelOf(TirePosition.AUXILIO));
        assertEquals(List.of("del-izq", "del-der"), TireLayout.SEIS_RUEDAS.positions().subList(0, 2).stream()
                .map(TirePosition::toJson).toList());
    }
}
