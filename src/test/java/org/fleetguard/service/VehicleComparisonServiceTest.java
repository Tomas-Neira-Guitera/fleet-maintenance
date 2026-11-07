package org.fleetguard.service;

import org.fleetguard.dto.DowntimeDto;
import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.RecurringDefectsDto;
import org.fleetguard.dto.VehicleComparisonDto;
import org.fleetguard.dto.VehicleComparisonDto.Averages;
import org.fleetguard.dto.VehicleComparisonDto.Row;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.KmReadingRow;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.service.KmPerDayEstimator.Reading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CAM-140: comparar vehículos para decidir si conviene reparar o reemplazar. Los números tienen que
 * coincidir con los del resto de la app, un vehículo sin datos se ve con ceros o "sin datos" y nunca con
 * un costo por km engañoso, y "Para revisar" señala solo al que está peor en dos indicadores.
 */
class VehicleComparisonServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
    private static final Instant FROM = Instant.parse("2026-10-01T03:00:00Z");

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final ExpenseService expenseService = mock(ExpenseService.class);
    private final DowntimeService downtimeService = mock(DowntimeService.class);
    private final RecurringDefectService recurringDefectService = mock(RecurringDefectService.class);
    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final VehicleComparisonService service = new VehicleComparisonService(vehicleRepository, expenseService,
            downtimeService, recurringDefectService,
            new KmPerDayEstimator(inspectionRepository, mock(MaintenanceCompletionRepository.class)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final List<Vehicle> fleet = new ArrayList<>();
    private final List<ExpenseDto> expenses = new ArrayList<>();
    private final List<DowntimeDto.Vehicle> downtime = new ArrayList<>();
    private final List<RecurringDefectsDto.Item> recurring = new ArrayList<>();
    private final List<KmReadingRow.FromInspection> readings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(vehicleRepository.findByActive(true)).thenReturn(fleet);
        when(expenseService.list(null, null, null, "2026-10-01", "2026-11-06")).thenReturn(expenses);
        when(downtimeService.get("2026-10-01", "2026-11-06", null))
                .thenAnswer(invocation -> new DowntimeDto(FROM, NOW, null, downtime));
        when(recurringDefectService.list(null)).thenAnswer(invocation -> new RecurringDefectsDto(3, 30, recurring));
        when(inspectionRepository.findKmReadings(any())).thenReturn(readings);
    }

    private Vehicle vehicle(String plate, long km) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Ford", "Cargo");
        Field id = Vehicle.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(vehicle, UUID.randomUUID());
        vehicle.setOdometerKm(km);
        vehicle.setYear(2018);
        fleet.add(vehicle);
        return vehicle;
    }

    private void expense(Vehicle vehicle, String origin, String amount) {
        expenses.add(new ExpenseDto(UUID.randomUUID().toString(), "2026-10-10T12:00:00Z", vehicle.getId().toString(),
                vehicle.getPlate(), "otro", "Gasto", new BigDecimal(amount), null, null, null, origin, null, null, null));
    }

    private void availability(Vehicle vehicle, double pct, double hours, int stops) {
        downtime.add(new DowntimeDto.Vehicle(vehicle.getId().toString(), vehicle.getPlate(), pct, hours, stops, null));
    }

    private void recurringControl(Vehicle vehicle, String itemId) {
        recurring.add(new RecurringDefectsDto.Item(vehicle.getId().toString(), vehicle.getPlate(), itemId, itemId, 3,
                NOW, List.of()));
    }

    private void reading(Vehicle vehicle, long km, Instant at) {
        readings.add(new KmReadingRow.FromInspection(vehicle.getId(), at, (double) km));
    }

    private Row row(VehicleComparisonDto dto, String plate) {
        return dto.vehicles().stream().filter(r -> r.plate().equals(plate)).findFirst().orElseThrow();
    }

    @Test
    void separaElGastoDeMantenimientoDelOperativoYCalculaElCostoPorKm() throws Exception {
        Vehicle truck = vehicle("AA111AA", 52000);
        expense(truck, "defect", "300000");
        expense(truck, "plan", "100000");
        expense(truck, "operating", "100000");
        reading(truck, 50000, FROM.minus(Duration.ofDays(2)));   // antes del período: es el km al empezar
        reading(truck, 52000, NOW.minus(Duration.ofDays(1)));
        availability(truck, 97.5, 18, 1);

        Row row = row(service.compare("2026-10-01", "2026-11-06"), "AA111AA");

        assertEquals(new BigDecimal("400000"), row.maintenanceCost());
        assertEquals(new BigDecimal("100000"), row.operatingCost());
        assertEquals(new BigDecimal("500000"), row.totalCost());
        assertEquals(2000L, row.kmDriven());
        assertEquals(new BigDecimal("200.00"), row.maintenanceCostPerKm());
        assertEquals(new BigDecimal("250.00"), row.totalCostPerKm());
        assertEquals(97.5, row.availabilityPct());
        assertEquals(1, row.stops());
        assertEquals(2018, row.year());
    }

    @Test
    void unVehiculoSinGastosNiDefectosSeVeConCerosYSinKmNoTieneCostoPorKm() throws Exception {
        vehicle("BB222BB", 10000);

        Row row = row(service.compare("2026-10-01", "2026-11-06"), "BB222BB");

        assertEquals(BigDecimal.ZERO, row.totalCost());
        assertEquals(0, row.recurringControls());
        assertEquals(0, row.stops());
        assertNull(row.kmDriven());
        assertNull(row.maintenanceCostPerKm());
        assertFalse(row.review().flagged());
    }

    @Test
    void elPromedioDeLaFlotaIgnoraLosQueNoTienenDato() throws Exception {
        Vehicle a = vehicle("AA111AA", 52000);
        Vehicle b = vehicle("BB222BB", 30000);
        expense(a, "defect", "200000");
        reading(a, 50000, FROM.minus(Duration.ofDays(2)));
        reading(a, 52000, NOW.minus(Duration.ofDays(1)));
        availability(a, 90, 10, 1);
        availability(b, 100, 0, 0);

        Averages fleet = service.compare("2026-10-01", "2026-11-06").fleet();

        assertEquals(new BigDecimal("100000.00"), fleet.totalCost());          // (200.000 + 0) / 2
        assertEquals(new BigDecimal("100.00"), fleet.maintenanceCostPerKm());  // solo el que tiene km
        assertEquals(95.0, fleet.availabilityPct());
    }

    @Test
    void paraRevisarConDosIndicadoresPeoresQueElPromedioYDiceCuales() throws Exception {
        Vehicle bad = vehicle("AA111AA", 52000);
        Vehicle good = vehicle("BB222BB", 52000);
        for (Vehicle v : List.of(bad, good)) {
            reading(v, 50000, FROM.minus(Duration.ofDays(2)));
            reading(v, 52000, NOW.minus(Duration.ofDays(1)));
        }
        expense(bad, "defect", "400000");
        expense(good, "defect", "100000");
        availability(bad, 80, 100, 3);
        availability(good, 99, 5, 1);
        recurringControl(good, "ext-luces");   // good está peor solo en esto

        VehicleComparisonDto dto = service.compare("2026-10-01", "2026-11-06");

        assertTrue(row(dto, "AA111AA").review().flagged());
        assertEquals(List.of(VehicleComparisonService.REASON_COST_PER_KM, VehicleComparisonService.REASON_AVAILABILITY),
                row(dto, "AA111AA").review().reasons());
        assertFalse(row(dto, "BB222BB").review().flagged());
        assertEquals(List.of(), row(dto, "BB222BB").review().reasons());
    }

    @Test
    void cuentaLosControlesConFallasRecurrentesDeCadaVehiculo() throws Exception {
        Vehicle truck = vehicle("AA111AA", 10000);
        recurringControl(truck, "ext-luces");
        recurringControl(truck, "ext-fugas");

        assertEquals(2, row(service.compare("2026-10-01", "2026-11-06"), "AA111AA").recurringControls());
    }

    @Test
    void lasFilasVanPorPatente() throws Exception {
        vehicle("ZZ999ZZ", 1);
        vehicle("AA111AA", 1);

        assertEquals(List.of("AA111AA", "ZZ999ZZ"),
                service.compare("2026-10-01", "2026-11-06").vehicles().stream().map(Row::plate).toList());
    }

    // --- Los km del período ---

    private static Reading r(long km, Instant at) {
        return new Reading(at, km);
    }

    @Test
    void losKmVanDeLaUltimaLecturaAntesDelPeriodoALaUltimaDentro() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-11-01T00:00:00Z");
        List<Reading> readings = List.of(r(40000, start.minus(Duration.ofDays(30))), r(41000, start.minus(Duration.ofDays(3))),
                r(42500, start.plus(Duration.ofDays(10))), r(44000, end.minus(Duration.ofDays(1))),
                r(45000, end.plus(Duration.ofDays(2))));

        assertEquals(new VehicleComparisonService.KmSpan(3000, start),
                VehicleComparisonService.kmDriven(readings, start, end, 5000));
    }

    @Test
    void sinLecturaAntesDelPeriodoLosKmCuentanDesdeLaPrimeraDentro() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-11-01T00:00:00Z");
        Instant firstReading = Instant.parse("2026-10-20T12:00:00Z");

        assertEquals(new VehicleComparisonService.KmSpan(300, firstReading), VehicleComparisonService.kmDriven(
                List.of(r(42500, firstReading), r(42800, end.minus(Duration.ofDays(1)))), start, end, 5000));
    }

    @Test
    void siLasLecturasArrancanTardeElCostoPorKmSoloTomaLosGastosDesdeAhi() throws Exception {
        // Una reparación de $1.000.000 antes de la primera lectura no se divide por los 300 km de después:
        // daría $3.333/km y lo marcaría "Para revisar". Sí cuenta en el gasto del período.
        Vehicle truck = vehicle("AA111AA", 42800);
        expenses.add(new ExpenseDto("e-1", "2026-10-05T12:00:00Z", truck.getId().toString(), "AA111AA", "otro",
                "Reparación", new BigDecimal("1000000"), null, null, null, "defect", null, null, null));
        expenses.add(new ExpenseDto("e-2", "2026-10-25T12:00:00Z", truck.getId().toString(), "AA111AA", "otro",
                "Aceite", new BigDecimal("30000"), null, null, null, "plan", null, null, null));
        reading(truck, 42500, Instant.parse("2026-10-20T12:00:00Z"));
        reading(truck, 42800, NOW.minus(Duration.ofDays(1)));

        Row row = row(service.compare("2026-10-01", "2026-11-06"), "AA111AA");

        assertEquals(new BigDecimal("1030000"), row.maintenanceCost());
        assertEquals(300L, row.kmDriven());
        assertEquals(new BigDecimal("100.00"), row.maintenanceCostPerKm());
    }

    @Test
    void unaPrimeraLecturaMalCargadaSinOtraAnteriorNoCuenta() {
        // Dado de alta con 309.000 km; la primera inspección se cargó con 310. Sin descartarla, el
        // vehículo habría andado 309.090 km en el período.
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-11-01T00:00:00Z");
        List<Reading> readings = List.of(r(310, start.minus(Duration.ofDays(60))), r(309200, start.minus(Duration.ofDays(2))),
                r(309400, end.minus(Duration.ofDays(1))));

        assertEquals(200L, VehicleComparisonService.kmDriven(readings, start, end, 5000).km());
    }

    @Test
    void conUnaSolaLecturaOSinLecturasNoHayKm() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-11-01T00:00:00Z");

        assertNull(VehicleComparisonService.kmDriven(List.of(), start, end, 5000));
        assertNull(VehicleComparisonService.kmDriven(List.of(r(42500, start.plus(Duration.ofDays(10)))), start, end, 5000));
        // Una sola lectura antes del período tampoco: no se sabe cuánto anduvo después.
        assertNull(VehicleComparisonService.kmDriven(List.of(r(42500, start.minus(Duration.ofDays(10)))), start, end, 5000));
    }

    @Test
    void elCostoPorKmDeLaFlotaEsElGastoSobreLosKmYNoElPromedioDeLosCocientes() throws Exception {
        // Uno anduvo 100 km con $100.000 ($1.000/km) y otro 4.000 km con $100.000 ($25/km). El promedio de
        // los cocientes daría $512,50/km y ninguno quedaría peor que el promedio por costo; la flota gasta
        // $200.000 en 4.100 km: $48,78/km.
        Vehicle few = vehicle("AA111AA", 50100);
        Vehicle many = vehicle("BB222BB", 54000);
        reading(few, 50000, FROM.minus(Duration.ofDays(2)));
        reading(few, 50100, NOW.minus(Duration.ofDays(1)));
        reading(many, 50000, FROM.minus(Duration.ofDays(2)));
        reading(many, 54000, NOW.minus(Duration.ofDays(1)));
        expense(few, "defect", "100000");
        expense(many, "defect", "100000");

        VehicleComparisonDto dto = service.compare("2026-10-01", "2026-11-06");

        assertEquals(new BigDecimal("48.78"), dto.fleet().maintenanceCostPerKm());
        // El caro queda por encima del promedio y el barato por debajo, como corresponde.
        assertTrue(row(dto, "AA111AA").maintenanceCostPerKm().compareTo(dto.fleet().maintenanceCostPerKm()) > 0);
        assertTrue(row(dto, "BB222BB").maintenanceCostPerKm().compareTo(dto.fleet().maintenanceCostPerKm()) < 0);
    }

    @Test
    void laMarcaSeDecideContraElPromedioSinRedondear() {
        // Disponibilidades de 99,94 y 99,95: el promedio real es 99,945 y redondeado a un decimal sería
        // 99,9, que dejaría al de 99,94 como si no estuviera por debajo.
        Row low = comparisonRow("AA", 99.94, 1);
        Row high = comparisonRow("BB", 99.95, 0);

        VehicleComparisonDto.Review review = VehicleComparisonService.review(low,
                VehicleComparisonService.averages(List.of(low, high), false));

        assertTrue(review.flagged());
        assertEquals(List.of(VehicleComparisonService.REASON_AVAILABILITY, VehicleComparisonService.REASON_RECURRING),
                review.reasons());
    }

    private static Row comparisonRow(String plate, double availability, int recurring) {
        return new Row(plate, plate, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                null, null, availability, 0, 0, recurring, null);
    }

    @Test
    void unVehiculoQueNoSeMovioTiene0KmYNoTieneCostoPorKm() throws Exception {
        Vehicle truck = vehicle("AA111AA", 50000);
        expense(truck, "defect", "1000");
        reading(truck, 50000, FROM.minus(Duration.ofDays(2)));
        reading(truck, 50000, NOW.minus(Duration.ofDays(1)));

        Row row = row(service.compare("2026-10-01", "2026-11-06"), "AA111AA");

        assertEquals(0L, row.kmDriven());
        assertNull(row.maintenanceCostPerKm());
    }

    @Test
    void unaLecturaMalCargadaNoCuentaParaLosKm() throws Exception {
        // La de 310 es menor a una anterior: la ignora igual que la fecha estimada (CAM-137).
        Vehicle truck = vehicle("AA111AA", 52000);
        reading(truck, 50000, FROM.minus(Duration.ofDays(2)));
        reading(truck, 310, FROM.plus(Duration.ofDays(3)));
        reading(truck, 52000, NOW.minus(Duration.ofDays(1)));

        assertEquals(2000L, row(service.compare("2026-10-01", "2026-11-06"), "AA111AA").kmDriven());
    }
}
