package org.fleetguard.service;

import org.fleetguard.entity.DocumentAlertStage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CAM-127: estado de un documento y qué aviso le toca. Vale hasta el día del vencimiento inclusive. */
class DocumentAlertRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @Test
    void estado() {
        assertEquals("sin_vencimiento", DocumentAlertRules.status(null, TODAY, 15));
        assertEquals("al_dia", DocumentAlertRules.status(TODAY.plusDays(16), TODAY, 15));
        assertEquals("por_vencer", DocumentAlertRules.status(TODAY.plusDays(15), TODAY, 15));
        assertEquals("por_vencer", DocumentAlertRules.status(TODAY, TODAY, 15));
        assertEquals("vencido", DocumentAlertRules.status(TODAY.minusDays(1), TODAY, 15));
    }

    @Test
    void etapaDelAviso() {
        assertNull(DocumentAlertRules.stage(null, TODAY, 15));
        assertNull(DocumentAlertRules.stage(TODAY.plusDays(16), TODAY, 15));
        assertEquals(DocumentAlertStage.DUE_SOON, DocumentAlertRules.stage(TODAY.plusDays(15), TODAY, 15));
        assertEquals(DocumentAlertStage.DUE_SOON, DocumentAlertRules.stage(TODAY.plusDays(1), TODAY, 15));
        assertEquals(DocumentAlertStage.DUE_TODAY, DocumentAlertRules.stage(TODAY, TODAY, 15));
        assertEquals(DocumentAlertStage.EXPIRED, DocumentAlertRules.stage(TODAY.minusDays(1), TODAY, 15));
    }

    @Test
    void textos() {
        assertEquals("Documento por vencer", DocumentAlertRules.title(DocumentAlertStage.DUE_SOON));
        assertEquals("Documento por vencer", DocumentAlertRules.title(DocumentAlertStage.DUE_TODAY));
        assertEquals("Documento vencido", DocumentAlertRules.title(DocumentAlertStage.EXPIRED));
        assertEquals("VTV / RTO de AB123CD: vence el 08/11/2026 (faltan 10 días).",
                DocumentAlertRules.message(DocumentAlertStage.DUE_SOON, "VTV / RTO", "AB123CD", TODAY.plusDays(10), TODAY));
        assertEquals("Seguro de AB123CD: vence el 30/10/2026 (falta 1 día).",
                DocumentAlertRules.message(DocumentAlertStage.DUE_SOON, "Seguro", "AB123CD", TODAY.plusDays(1), TODAY));
        assertEquals("Seguro de AB123CD: vence hoy (29/10/2026). Desde mañana el vehículo queda no disponible si no se carga la renovación.",
                DocumentAlertRules.message(DocumentAlertStage.DUE_TODAY, "Seguro", "AB123CD", TODAY, TODAY));
        assertEquals("Seguro de AB123CD: venció el 28/10/2026. El vehículo queda no disponible hasta que se cargue la renovación.",
                DocumentAlertRules.message(DocumentAlertStage.EXPIRED, "Seguro", "AB123CD", TODAY.minusDays(1), TODAY));
    }
}
