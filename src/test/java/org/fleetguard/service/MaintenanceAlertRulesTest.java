package org.fleetguard.service;

import org.fleetguard.entity.MaintenanceAlertStage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CAM-118: cuándo corresponde cada aviso de vencimiento y qué dice. */
class MaintenanceAlertRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    void porFechaAvisaAlEntrarEnLaAnticipacion() {
        assertNull(MaintenanceAlertRules.stage(null, TODAY.plusDays(16), 0, TODAY, null, 15));
        assertEquals(MaintenanceAlertStage.DUE_SOON, MaintenanceAlertRules.stage(null, TODAY.plusDays(15), 0, TODAY, null, 15));
        assertEquals(MaintenanceAlertStage.DUE_SOON, MaintenanceAlertRules.stage(null, TODAY, 0, TODAY, null, 15));
        assertEquals(MaintenanceAlertStage.OVERDUE, MaintenanceAlertRules.stage(null, TODAY.minusDays(1), 0, TODAY, null, 15));
    }

    @Test
    void porKmAvisaAlEntrarEnLaAnticipacion() {
        assertNull(MaintenanceAlertRules.stage(20_000L, null, 18_999, TODAY, 1000, null));
        assertEquals(MaintenanceAlertStage.DUE_SOON, MaintenanceAlertRules.stage(20_000L, null, 19_000, TODAY, 1000, null));
        assertEquals(MaintenanceAlertStage.DUE_SOON, MaintenanceAlertRules.stage(20_000L, null, 20_000, TODAY, 1000, null));
        assertEquals(MaintenanceAlertStage.OVERDUE, MaintenanceAlertRules.stage(20_000L, null, 20_001, TODAY, 1000, null));
    }

    @Test
    void laAnticipacionNoDependeDelUmbralDelDashboard() {
        // 10 días: el dashboard (7) lo ve al día; con 15 de anticipación ya se avisa.
        assertEquals(MaintenanceAlertStage.DUE_SOON, MaintenanceAlertRules.stage(null, TODAY.plusDays(10), 0, TODAY, null, 15));
        assertNull(MaintenanceAlertRules.stage(null, TODAY.plusDays(10), 0, TODAY, null, 5));
    }

    @Test
    void elCicloCambiaConElProximoVencimiento() {
        assertEquals(MaintenanceAlertRules.cycleKey(20_000L, TODAY), MaintenanceAlertRules.cycleKey(20_000L, TODAY));
        assertNotEquals(MaintenanceAlertRules.cycleKey(20_000L, TODAY), MaintenanceAlertRules.cycleKey(30_000L, TODAY));
        assertNotEquals(MaintenanceAlertRules.cycleKey(null, TODAY), MaintenanceAlertRules.cycleKey(null, TODAY.plusDays(90)));
    }

    @Test
    void mensajes() {
        assertEquals("Aceite de AB123CD: faltan 5 días (vence el 28/10/2026).",
                MaintenanceAlertRules.message(MaintenanceAlertStage.DUE_SOON, "Aceite", "AB123CD", null, TODAY.plusDays(5), 0, TODAY));
        assertEquals("Aceite de AB123CD: vence hoy.",
                MaintenanceAlertRules.message(MaintenanceAlertStage.DUE_SOON, "Aceite", "AB123CD", null, TODAY, 0, TODAY));
        assertEquals("Frenos de AB123CD: faltan 800 km (vence a los 20.000 km).",
                MaintenanceAlertRules.message(MaintenanceAlertStage.DUE_SOON, "Frenos", "AB123CD", 20_000L, null, 19_200, TODAY));
        assertEquals("Frenos de AB123CD: venció el 22/10/2026 (hace 1 día) y se pasó por 1.500 km (vencía a los 20.000 km).",
                MaintenanceAlertRules.message(MaintenanceAlertStage.OVERDUE, "Frenos", "AB123CD", 20_000L, TODAY.minusDays(1),
                        21_500, TODAY));
    }
}
