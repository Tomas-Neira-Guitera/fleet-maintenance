package org.fleetguard.service;

import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.KmReadingRow;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.service.KmPerDayEstimator.Reading;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-137: km por día de un vehículo para estimar cuándo vence un plan por km. Si el cálculo se
 * deja engañar por un km mal cargado, el admin ve "vence aprox. mañana" en un plan que vence en
 * meses, o al revés.
 */
class KmPerDayEstimatorTest {

    private static final Instant NOW = Instant.parse("2026-11-05T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private static Reading reading(long km, int daysAgo) {
        return new Reading(NOW.minus(Duration.ofDays(daysAgo)), km);
    }

    @Test
    void conLecturasSuficientesDivideLoRecorridoPorLosDias() {
        Double pace = KmPerDayEstimator.kmPerDay(List.of(reading(47000, 30), reading(48500, 15), reading(50000, 0)),
                50000, NOW);

        assertEquals(100.0, pace, 1e-9);
    }

    @Test
    void elOrdenEnQueLleganLasLecturasNoImporta() {
        Double pace = KmPerDayEstimator.kmPerDay(List.of(reading(50000, 0), reading(47000, 30)), 50000, NOW);

        assertEquals(100.0, pace, 1e-9);
    }

    @Test
    void conUnaSolaLecturaNoHayEstimacion() {
        assertNull(KmPerDayEstimator.kmPerDay(List.of(reading(50000, 2)), 50000, NOW));
        assertNull(KmPerDayEstimator.kmPerDay(List.of(), 50000, NOW));
    }

    @Test
    void conMenosDeUnaSemanaEntreLaPrimeraYLaUltimaNoHayEstimacion() {
        assertNull(KmPerDayEstimator.kmPerDay(List.of(reading(49000, 6), reading(50000, 0)), 50000, NOW));
        assertEquals(1000.0 / 7, KmPerDayEstimator.kmPerDay(List.of(reading(49000, 7), reading(50000, 0)), 50000, NOW),
                1e-9);
    }

    @Test
    void lasLecturasDeHaceMasDe60DiasNoEntranEnElPromedio() {
        // La de hace 90 días daría 3.000 km en 90 días; con las de la ventana son 1.000 en 20.
        Double pace = KmPerDayEstimator.kmPerDay(List.of(reading(47000, 90), reading(49000, 20), reading(50000, 0)),
                50000, NOW);

        assertEquals(50.0, pace, 1e-9);
        assertNull(KmPerDayEstimator.kmPerDay(List.of(reading(47000, 90), reading(50000, 0)), 50000, NOW));
    }

    @Test
    void unVehiculoQueNoSeMovioNoTieneEstimacion() {
        assertNull(KmPerDayEstimator.kmPerDay(List.of(reading(50000, 30), reading(50000, 0)), 50000, NOW));
    }

    @Test
    void unaLecturaConMenosKmQueUnaAnteriorSeIgnora() {
        // Caso AA846JQ (CAM-90): una inspección con 310 km en un vehículo de 309.400.
        Double pace = KmPerDayEstimator.kmPerDay(
                List.of(reading(306400, 30), reading(310, 20), reading(309400, 0)), 309400, NOW);

        assertEquals(100.0, pace, 1e-9);
    }

    @Test
    void unaLecturaMalCargadaAlPrincipioDeLaVentanaSeIgnoraPorLaHistoriaAnterior() {
        // Antes de la ventana ya se había leído 300.000: la de 15.000 de hace 40 días es un error,
        // y si contara daría unos 7.360 km por día.
        Double pace = KmPerDayEstimator.kmPerDay(
                List.of(reading(300000, 70), reading(15000, 40), reading(306400, 30), reading(309400, 0)),
                309400, NOW);

        assertEquals(100.0, pace, 1e-9);
    }

    @Test
    void unaLecturaConMasKmQueElVehiculoSeIgnora() {
        // Un 500.000 tipeado que el vehículo no aceptó (CAM-90, salto máximo).
        Double pace = KmPerDayEstimator.kmPerDay(
                List.of(reading(47000, 30), reading(500000, 10), reading(50000, 0)), 50000, NOW);

        assertEquals(100.0, pace, 1e-9);
    }

    @Test
    void conTodasLasLecturasInvalidasMenosUnaNoHayEstimacion() {
        assertNull(KmPerDayEstimator.kmPerDay(List.of(reading(50000, 30), reading(310, 0)), 50000, NOW));
    }

    @Test
    void unaPrimeraLecturaMalCargadaSinOtraAnteriorSeDescartaPorElRitmoImposible() {
        // Dado de alta con 309.000 (no deja lectura) y la primera inspección cargada con 310: sin
        // otra anterior, sería (309.400 - 310) / 20, unos 15.000 km por día.
        assertEquals(100.0, KmPerDayEstimator.kmPerDay(
                List.of(reading(310, 20), reading(308400, 10), reading(309400, 0)), 309400, NOW), 1e-9);
        assertNull(KmPerDayEstimator.kmPerDay(List.of(reading(310, 20), reading(309400, 0)), 309400, NOW));
    }

    @Test
    void unaPrimeraLecturaConUnErrorMedianoSeDescartaPorElSaltoHastaLaSiguiente() {
        // 290.000 en vez de 309.000 (un 9 por un 0): hasta la última da 970 km por día, que pasa el
        // tope, pero hay 18.400 km hasta la lectura siguiente, más de lo que el vehículo acepta.
        assertEquals(100.0, KmPerDayEstimator.kmPerDay(
                List.of(reading(290000, 20), reading(308400, 10), reading(309400, 0)), 309400, NOW), 1e-9);
        assertNull(KmPerDayEstimator.kmPerDay(List.of(reading(290000, 20), reading(309400, 0)), 309400, NOW));
    }

    @Test
    void elSaltoMaximoEsElConfigurado() {
        List<Reading> readings = List.of(reading(44000, 10), reading(50000, 0));

        assertNull(KmPerDayEstimator.kmPerDay(readings, 50000, NOW, 5000));
        assertEquals(600.0, KmPerDayEstimator.kmPerDay(readings, 50000, NOW, 8000), 1e-9);
    }

    @Test
    void unRitmoAltoPeroPosibleSeAcepta() {
        assertEquals(1500.0, KmPerDayEstimator.kmPerDay(List.of(reading(40000, 10), reading(44500, 7),
                reading(49000, 4), reading(52000, 2), reading(55000, 0)), 55000, NOW), 1e-9);
    }

    @Test
    void unaLecturaEnElFuturoSeIgnora() {
        Double pace = KmPerDayEstimator.kmPerDay(
                List.of(reading(47000, 30), reading(50000, 0), new Reading(NOW.plus(Duration.ofDays(5)), 50000)),
                50000, NOW);

        assertEquals(100.0, pace, 1e-9);
    }

    @Test
    void elMantenimientoDelMismoDiaNoDescartaElPreViajeDeEsaManana() {
        // El mantenimiento solo guarda el día y cuenta desde las 00:00 argentinas: por hora quedaría
        // antes del pre-viaje de las 06:00, con más km, y el pre-viaje pasaría por un error.
        Instant preTrip = Instant.parse("2026-10-26T09:00:00Z");      // 06:00 en Argentina
        Instant maintenance = Instant.parse("2026-10-26T03:00:00Z");  // 00:00 en Argentina
        Double pace = KmPerDayEstimator.kmPerDay(
                List.of(new Reading(maintenance, 50050), new Reading(preTrip, 50000), reading(51000, 0)), 51000, NOW);

        // Del pre-viaje a ahora: 10 días y 6 horas.
        assertEquals(1000 / 10.25, pace, 1e-9);
    }

    @Test
    void laFechaEstimadaRedondeaLosDiasParaArriba() {
        assertEquals(TODAY.plusDays(10), KmPerDayEstimator.estimatedDueDate(1000L, 100.0, TODAY));
        assertEquals(TODAY.plusDays(11), KmPerDayEstimator.estimatedDueDate(1001L, 100.0, TODAY));
        assertEquals(TODAY.plusDays(1), KmPerDayEstimator.estimatedDueDate(5L, 100.0, TODAY));
        // 1.000 km a 99,99999 km por día no son 11 días por un error de punto flotante.
        assertEquals(TODAY.plusDays(10), KmPerDayEstimator.estimatedDueDate(1000L, 1000 / 10.000000001, TODAY));
    }

    @Test
    void sinEstimacionOConElPlanVencidoNoHayFecha() {
        assertNull(KmPerDayEstimator.estimatedDueDate(1000L, null, TODAY));
        assertNull(KmPerDayEstimator.estimatedDueDate(null, 100.0, TODAY));
        assertNull(KmPerDayEstimator.estimatedDueDate(0L, 100.0, TODAY));
        assertNull(KmPerDayEstimator.estimatedDueDate(-500L, 100.0, TODAY));
    }

    @Test
    void juntaInspeccionesYMantenimientosDeCadaVehiculo() throws Exception {
        InspectionRepository inspections = mock(InspectionRepository.class);
        MaintenanceCompletionRepository completions = mock(MaintenanceCompletionRepository.class);
        KmPerDayEstimator estimator = new KmPerDayEstimator(inspections, completions);
        Vehicle moving = vehicle(50000);
        Vehicle alone = vehicle(80000);
        when(inspections.findKmReadings(any())).thenReturn(List.of(
                new KmReadingRow.FromInspection(moving.getId(), NOW, 50000.9),
                new KmReadingRow.FromInspection(alone.getId(), NOW, 80000.0)));
        when(completions.findKmReadings(any())).thenReturn(List.of(
                new KmReadingRow.FromCompletion(moving.getId(), LocalDate.of(2026, 10, 6), 47000L)));

        Map<UUID, Double> pace = estimator.kmPerDay(List.of(moving, alone), NOW);

        // Del 6/10 a la medianoche argentina (03:00 UTC) al 5/11 15:00 UTC: 30,5 días.
        assertEquals(3000 / 30.5, pace.get(moving.getId()), 1e-9);
        assertFalse(pace.containsKey(alone.getId()));
    }

    @Test
    void sinVehiculosNoConsultaLaBase() {
        InspectionRepository inspections = mock(InspectionRepository.class);
        KmPerDayEstimator estimator = new KmPerDayEstimator(inspections, mock(MaintenanceCompletionRepository.class));

        assertTrue(estimator.kmPerDay(List.of(), NOW).isEmpty());
        verify(inspections, never()).findKmReadings(any());
    }

    private static Vehicle vehicle(long km) throws Exception {
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        Field field = Vehicle.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(vehicle, UUID.randomUUID());
        vehicle.setOdometerKm(km);
        return vehicle;
    }
}
