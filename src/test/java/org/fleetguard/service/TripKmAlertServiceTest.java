package org.fleetguard.service;

import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripKmAlertReason;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.event.TripKmAnomalyEvent;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripInspectionKmRow;
import org.fleetguard.service.TripKmAlertService.Rules;
import org.fleetguard.service.TripKmAlertService.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CAM-152: al cerrar un viaje se avisa si su km es muy distinto al habitual del vehículo, o si el
 * chofer declaró menos km de los que ya tenía. Un falso aviso en cada viaje haría que el admin deje
 * de mirarlos; uno que falta deja pasar un error de carga o un uso indebido del camión.
 */
class TripKmAlertServiceTest {

    private static final Rules RULES = new Rules(3, 10, 2, 5000);

    // --- La regla ---

    @Test
    void unViajeDeMasDelDobleDelPromedioEsAboveAverage() {
        Verdict verdict = TripKmAlertService.evaluate(50000L, 50700, 50000, List.of(300L, 300L, 300L), RULES);

        assertEquals(new Verdict(TripKmAlertReason.ABOVE_AVERAGE, 700L, 300L), verdict);
    }

    @Test
    void unViajeDeMenosDeLaMitadDelPromedioEsBelowAverage() {
        Verdict verdict = TripKmAlertService.evaluate(50000L, 50100, 50000, List.of(300L, 300L, 300L), RULES);

        assertEquals(new Verdict(TripKmAlertReason.BELOW_AVERAGE, 100L, 300L), verdict);
    }

    @Test
    void justoElDobleOJustoLaMitadNoAvisa() {
        assertNull(TripKmAlertService.evaluate(50000L, 50600, 50000, List.of(300L, 300L, 300L), RULES));
        assertNull(TripKmAlertService.evaluate(50000L, 50150, 50000, List.of(300L, 300L, 300L), RULES));
    }

    @Test
    void unViajeNormalNoAvisa() {
        assertNull(TripKmAlertService.evaluate(50000L, 50350, 50000, List.of(300L, 250L, 400L), RULES));
    }

    @Test
    void sinViajesSuficientesNoHayPromedioYNoAvisa() {
        assertNull(TripKmAlertService.evaluate(50000L, 59000, 50000, List.of(300L, 300L), RULES));
        assertNull(TripKmAlertService.evaluate(50000L, 59000, 50000, List.of(), RULES));
    }

    @Test
    void losViajesSinMovimientoONegativosODeMasDelSaltoNoCuentanParaElPromedio() {
        // Sin los descartados quedan 300, 300 y 300: un viaje de 700 es más del doble.
        List<Long> previous = List.of(0L, 300L, -200L, 300L, 9000L, 300L);

        assertEquals(new Verdict(TripKmAlertReason.ABOVE_AVERAGE, 700L, 300L),
                TripKmAlertService.evaluate(50000L, 50700, 50000, previous, RULES));
        // Y con solo dos que sirven, no hay promedio.
        assertNull(TripKmAlertService.evaluate(50000L, 50700, 50000, List.of(0L, 300L, 9000L, 300L), RULES));
    }

    @Test
    void elPromedioUsaSoloLosUltimosViajes() {
        // Los 10 más recientes son de 1.000 km; los viejos de 100 bajarían el promedio.
        List<Long> previous = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            previous.add(1000L);
        }
        for (int i = 0; i < 10; i++) {
            previous.add(100L);
        }

        assertNull(TripKmAlertService.evaluate(50000L, 51000, 50000, previous, RULES));
        assertEquals(1000L, TripKmAlertService.evaluate(50000L, 50300, 50000, previous, RULES).averageKm());
    }

    @Test
    void unKmDeclaradoMenorAlDelVehiculoAvisaAunqueNoHayaPromedio() {
        Verdict verdict = TripKmAlertService.evaluate(50000L, 49500, 50000, List.of(), RULES);

        assertEquals(new Verdict(TripKmAlertReason.BELOW_VEHICLE_KM, -500L, null), verdict);
    }

    @Test
    void unKmDeclaradoMenorAlDelVehiculoGanaSobreElPromedio() {
        Verdict verdict = TripKmAlertService.evaluate(30000L, 49500, 50000, List.of(300L, 300L, 300L), RULES);

        assertEquals(TripKmAlertReason.BELOW_VEHICLE_KM, verdict.reason());
        assertEquals(300L, verdict.averageKm());
    }

    @Test
    void sinKmEnElPreViajeSoloSeRevisaContraElKmDelVehiculo() {
        assertNull(TripKmAlertService.evaluate(null, 59000, 50000, List.of(300L, 300L, 300L), RULES));
        assertEquals(new Verdict(TripKmAlertReason.BELOW_VEHICLE_KM, null, 300L),
                TripKmAlertService.evaluate(null, 49000, 50000, List.of(300L, 300L, 300L), RULES));
    }

    @Test
    void unPreViajeQueCam90DescartoNoSeUsaParaLosKmDelViaje() {
        List<Long> previous = List.of(300L, 300L, 300L);
        // Un 400.000 tipeado (salto de más de 5.000) que el vehículo ignoró: daría un viaje de -90.400.
        assertNull(TripKmAlertService.evaluate(400000L, 309600, 309400, previous, RULES));
        // Un 15.000 menor al actual que el vehículo ignoró: daría un viaje de 294.600.
        assertNull(TripKmAlertService.evaluate(15000L, 309600, 309400, previous, RULES));
        // Y si además el post-viaje es menor al vehículo, se avisa por eso, sin km del viaje.
        assertEquals(new Verdict(TripKmAlertReason.BELOW_VEHICLE_KM, null, 300L),
                TripKmAlertService.evaluate(15000L, 309000, 309400, previous, RULES));
    }

    @Test
    void unPostViajeConUnSaltoEnormeYUnPromedioAvisa() {
        // El pre-viaje está bien (es el km del vehículo): el error es del post-viaje.
        assertEquals(new Verdict(TripKmAlertReason.ABOVE_AVERAGE, 1350000L, 300L),
                TripKmAlertService.evaluate(150000L, 1500000, 150000, List.of(300L, 300L, 300L), RULES));
    }

    @Test
    void unViajeDe0KmAvisaAunqueNoCuenteParaElPromedio() {
        assertEquals(new Verdict(TripKmAlertReason.BELOW_AVERAGE, 0L, 300L),
                TripKmAlertService.evaluate(50000L, 50000, 50000, List.of(300L, 300L, 300L), RULES));
    }

    // --- Con la base ---

    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final List<Object> events = new ArrayList<>();
    private final TripKmAlertService service = new TripKmAlertService(inspectionRepository, events::add, 3, 10, 2, 5000);

    private Vehicle vehicle;
    private Trip trip;

    @BeforeEach
    void setUp() throws Exception {
        vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        setId(vehicle, UUID.randomUUID());
        trip = new Trip(vehicle, Instant.parse("2026-11-05T08:00:00Z"));
        setId(trip, UUID.randomUUID());
        trip.close(Instant.parse("2026-11-05T18:00:00Z"), null, "chofer1");
    }

    private void preTripKm(Double km) {
        Inspection pre = new Inspection(trip, vehicle.getId(), "d", "chofer1", InspectionType.PRE_TRIP,
                Instant.parse("2026-11-05T08:00:00Z"), km, null, false);
        when(inspectionRepository.findFirstByTrip_IdAndType(trip.getId(), InspectionType.PRE_TRIP))
                .thenReturn(Optional.of(pre));
    }

    /** Viajes anteriores con esos km, del más viejo al más nuevo, de a un día. */
    private void previousTrips(long... kms) {
        List<TripInspectionKmRow> rows = new ArrayList<>();
        long odometer = 10000;
        for (int i = 0; i < kms.length; i++) {
            UUID id = UUID.randomUUID();
            Instant ended = Instant.parse("2026-10-01T18:00:00Z").plusSeconds(86400L * i);
            rows.add(new TripInspectionKmRow(id, ended, InspectionType.PRE_TRIP, (double) odometer));
            rows.add(new TripInspectionKmRow(id, ended, InspectionType.POST_TRIP, odometer + kms[i] + 0.9));
            odometer += kms[i];
        }
        when(inspectionRepository.findClosedTripKm(vehicle.getId(), trip.getId())).thenReturn(rows);
    }

    @Test
    void unViajeFueraDeLoNormalQuedaMarcadoYPublicaElAviso() {
        preTripKm(50000.4);
        previousTrips(300, 310, 290);

        service.check(trip, 50700, 50000, "chofer1");

        assertEquals(TripKmAlertReason.ABOVE_AVERAGE, trip.getKmAlertReason());
        assertEquals(700L, trip.getKmAlertTripKm());
        assertEquals(300L, trip.getKmAlertAverageKm());
        assertEquals(50700L, trip.getKmAlertDeclaredKm());
        assertEquals(50000L, trip.getKmAlertVehicleKm());
        TripKmAnomalyEvent event = (TripKmAnomalyEvent) events.get(0);
        assertEquals("AB123CD", event.plate());
        assertEquals("chofer1", event.driverName());
        assertEquals(700L, event.tripKm());
    }

    @Test
    void unViajeNormalNoMarcaNiAvisa() {
        preTripKm(50000.0);
        previousTrips(300, 310, 290);

        service.check(trip, 50320, 50000, "chofer1");

        assertNull(trip.getKmAlertReason());
        assertEquals(0, events.size());
    }

    @Test
    void unViajeAnteriorSinPostViajeNoCuentaParaElPromedio() {
        preTripKm(50000.0);
        // Dos viajes completos y uno que se cerró en el pre-viaje por un defecto bloqueante (CAM-86).
        List<TripInspectionKmRow> rows = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID id = UUID.randomUUID();
            Instant ended = Instant.parse("2026-10-01T18:00:00Z").plusSeconds(86400L * i);
            rows.add(new TripInspectionKmRow(id, ended, InspectionType.PRE_TRIP, 10000.0));
            rows.add(new TripInspectionKmRow(id, ended, InspectionType.POST_TRIP, 10300.0));
        }
        rows.add(new TripInspectionKmRow(UUID.randomUUID(), Instant.parse("2026-10-05T08:00:00Z"),
                InspectionType.PRE_TRIP, 10600.0));
        when(inspectionRepository.findClosedTripKm(vehicle.getId(), trip.getId())).thenReturn(rows);

        service.check(trip, 59000, 50000, "chofer1");

        assertNull(trip.getKmAlertReason());
    }

    @Test
    void elPromedioTomaLosViajesMasRecientesPorFechaDeCierre() {
        preTripKm(50000.0);
        // Del más viejo al más nuevo: diez de 100 y después diez de 1.000.
        long[] kms = new long[20];
        for (int i = 0; i < 20; i++) {
            kms[i] = i < 10 ? 100 : 1000;
        }
        previousTrips(kms);

        service.check(trip, 51000, 50000, "chofer1");

        assertNull(trip.getKmAlertReason());
    }

    @Test
    void sinPreViajeNoHayKmDelViajeYNoSeComparaConElPromedio() {
        when(inspectionRepository.findFirstByTrip_IdAndType(any(), any())).thenReturn(Optional.empty());
        previousTrips(300, 300, 300);

        service.check(trip, 50700, 50000, "chofer1");

        assertNull(trip.getKmAlertReason());
        assertEquals(0, events.size());
    }

    @Test
    void unKmDeclaradoMenorAlDelVehiculoMarcaElViaje() {
        preTripKm(50000.0);
        previousTrips(300, 300, 300);

        service.check(trip, 49000, 50000, "chofer1");

        assertEquals(TripKmAlertReason.BELOW_VEHICLE_KM, trip.getKmAlertReason());
        assertEquals(1, events.size());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
