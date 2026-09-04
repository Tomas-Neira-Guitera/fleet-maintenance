package org.example.service;

import org.example.entity.MaintenanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaintenanceStatusCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    @Test
    void alDiaCuandoFaltaMasQueElUmbral() {
        // Scania R450 del mock: vence en 18 días.
        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                null, TODAY.plusDays(18), 45200, TODAY);
        assertEquals(MaintenanceStatus.AL_DIA, status);
    }

    @Test
    void alDiaCuandoFaltanMuchosKm() {
        // Ford Cargo del mock: faltan 12.400 km.
        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                84320L + 12400, null, 84320, TODAY);
        assertEquals(MaintenanceStatus.AL_DIA, status);
    }

    @Test
    void porVencerDentroDelUmbralDeDias() {
        // Mercedes-Benz Sprinter del mock: vence en 3 días.
        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                null, TODAY.plusDays(3), 102180, TODAY);
        assertEquals(MaintenanceStatus.POR_VENCER, status);
    }

    @Test
    void vencidoCuandoLaFechaYaPaso() {
        // Iveco Daily del mock: vencida hace 5 días.
        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                null, TODAY.minusDays(5), 156900, TODAY);
        assertEquals(MaintenanceStatus.VENCIDO, status);
    }

    @Test
    void vencidoCuandoElKmYaSePaso() {
        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                198760L, null, 198900, TODAY);
        assertEquals(MaintenanceStatus.VENCIDO, status);
    }

    @Test
    void bothUsaLaDimensionMasUrgente() {
        // Al día por km pero vencido por fecha -> gana el vencido.
        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                200000L, TODAY.minusDays(1), 100000, TODAY);
        assertEquals(MaintenanceStatus.VENCIDO, status);
    }

    @Test
    void healthScoreRestaPenalizacionesYNoBajaDeCero() {
        assertEquals(100, MaintenanceStatusCalculator.healthScore(List.of()));
        assertEquals(92, MaintenanceStatusCalculator.healthScore(List.of(MaintenanceStatus.POR_VENCER)));
        assertEquals(75, MaintenanceStatusCalculator.healthScore(List.of(MaintenanceStatus.VENCIDO)));
        assertEquals(0, MaintenanceStatusCalculator.healthScore(List.of(
                MaintenanceStatus.VENCIDO, MaintenanceStatus.VENCIDO, MaintenanceStatus.VENCIDO,
                MaintenanceStatus.VENCIDO, MaintenanceStatus.VENCIDO)));
    }

    @Test
    void urgencyPercentComparaKmYTiempoDeFormaProporcional() {
        // Falta 1200 de 10.000 km -> 88% consumido.
        double pctKm = MaintenanceStatusCalculator.urgencyPercent(0L, 10000L, null, null, 8800, TODAY);
        assertEquals(0.88, pctKm, 0.001);

        // Faltan 20 días de un intervalo de 30 -> 33% consumido.
        double pctTime = MaintenanceStatusCalculator.urgencyPercent(null, null, TODAY.minusDays(10), TODAY.plusDays(20), 0, TODAY);
        assertEquals(0.333, pctTime, 0.001);
    }
}
