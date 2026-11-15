package org.fleetguard.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// CAM-157: el catálogo de tipos define las cubiertas de cada vehículo y qué remolque arrastra.
class VehicleTypeTest {

    @Test
    void cadaTipoTieneSusCubiertasMasElAuxilio() {
        assertEquals(5, VehicleType.UTILITARIO.positions().size());
        assertEquals(7, VehicleType.CAMION_4X2.positions().size());
        assertEquals(11, VehicleType.CAMION_6X4.positions().size());
        assertEquals(7, VehicleType.TRACTOR_4X2.positions().size());
        assertEquals(11, VehicleType.TRACTOR_6X4.positions().size());
        assertEquals(9, VehicleType.SEMIRREMOLQUE_2_EJES.positions().size());
        assertEquals(13, VehicleType.SEMIRREMOLQUE_3_EJES.positions().size());
        assertEquals(9, VehicleType.ACOPLADO.positions().size());
        for (VehicleType type : VehicleType.values()) {
            assertTrue(type.has(TirePosition.AUXILIO));
            assertEquals(type, VehicleType.fromJson(type.toJson()));
        }
        assertEquals(List.of("e1-izq", "e1-der", "e2-izq-ext", "e2-izq-int", "e2-der-int", "e2-der-ext", "auxilio"),
                VehicleType.CAMION_4X2.positions().stream().map(TirePosition::toJson).toList());
    }

    @Test
    void elNombreDeLaPosicionDependeDelTipo() {
        assertEquals("Delantera izquierda", VehicleType.UTILITARIO.labelOf(TirePosition.E1_IZQ));
        assertEquals("Trasera derecha", VehicleType.UTILITARIO.labelOf(TirePosition.E2_DER));
        assertEquals("Trasera izquierda exterior", VehicleType.TRACTOR_4X2.labelOf(TirePosition.E2_IZQ_EXT));
        assertEquals("Eje 3 derecha interior", VehicleType.TRACTOR_6X4.labelOf(TirePosition.E3_DER_INT));
        assertEquals("Eje 1 izquierda exterior", VehicleType.SEMIRREMOLQUE_3_EJES.labelOf(TirePosition.E1_IZQ_EXT));
        assertEquals("Auxilio", VehicleType.ACOPLADO.labelOf(TirePosition.AUXILIO));
    }

    @Test
    void unTractorArrastraSemirremolquesYUnCamionRigidoAcoplados() {
        assertTrue(VehicleType.TRACTOR_4X2.canTow(VehicleType.SEMIRREMOLQUE_3_EJES));
        assertFalse(VehicleType.TRACTOR_4X2.canTow(VehicleType.ACOPLADO));
        assertTrue(VehicleType.CAMION_6X4.canTow(VehicleType.ACOPLADO));
        assertFalse(VehicleType.CAMION_4X2.canTow(VehicleType.SEMIRREMOLQUE_2_EJES));
        assertFalse(VehicleType.UTILITARIO.canTow(VehicleType.ACOPLADO));
        assertTrue(VehicleType.ACOPLADO.isTrailer());
        assertFalse(VehicleType.TRACTOR_6X4.isTrailer());
    }

    @Test
    void reconoceNombresAliasYDatosDeCuandoEraTextoLibre() {
        assertEquals(VehicleType.UTILITARIO, VehicleType.fromText("Furgón"));
        assertEquals(VehicleType.UTILITARIO, VehicleType.fromText("pickup"));
        assertEquals(VehicleType.CAMION_4X2, VehicleType.fromText("camion"));
        assertEquals(VehicleType.TRACTOR_6X4, VehicleType.fromText("Tractor 6x4"));
        assertEquals(VehicleType.SEMIRREMOLQUE_2_EJES, VehicleType.fromText("semirremolque_2_ejes"));
        assertEquals(VehicleType.CAMION_6X4, VehicleType.fromText("Camión rígido 6x2 / 6x4"));
        assertNull(VehicleType.fromText("colectivo"));
        assertNull(VehicleType.fromText(" "));
    }
}
