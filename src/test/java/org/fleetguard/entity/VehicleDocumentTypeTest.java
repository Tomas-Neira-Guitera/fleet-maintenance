package org.fleetguard.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CAM-127: el tipo de un documento sale de su nombre libre. */
class VehicleDocumentTypeTest {

    @Test
    void reconoceLosDocumentosConocidosPorSuNombre() {
        assertEquals(VehicleDocumentType.VTV, VehicleDocumentType.infer("VTV"));
        assertEquals(VehicleDocumentType.VTV, VehicleDocumentType.infer("Revisión técnica obligatoria (RTO)"));
        assertEquals(VehicleDocumentType.SEGURO, VehicleDocumentType.infer("Póliza La Segunda"));
        assertEquals(VehicleDocumentType.CEDULA_AZUL, VehicleDocumentType.infer("cédula AZUL de Juan"));
        assertEquals(VehicleDocumentType.CEDULA_VERDE, VehicleDocumentType.infer("Cédula verde"));
        assertEquals(VehicleDocumentType.HABILITACION_CARGA, VehicleDocumentType.infer("Habilitación de carga RUTA"));
        assertEquals(VehicleDocumentType.TITULO, VehicleDocumentType.infer("Título del automotor"));
    }

    @Test
    void loQueNoReconoceEsOtroYNoConfundePartesDePalabras() {
        assertEquals(VehicleDocumentType.OTRO, VehicleDocumentType.infer("Permiso municipal"));
        assertEquals(VehicleDocumentType.OTRO, VehicleDocumentType.infer("Subtitulo"));
        assertEquals(VehicleDocumentType.OTRO, VehicleDocumentType.infer(null));
        assertEquals("poliza la segunda", VehicleDocumentType.normalize("  Póliza, La-Segunda! "));
    }
}
