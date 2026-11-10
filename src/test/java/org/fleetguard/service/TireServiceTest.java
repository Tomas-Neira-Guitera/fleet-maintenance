package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.MountTireRequest;
import org.fleetguard.dto.RetireTireRequest;
import org.fleetguard.dto.RotateTiresRequest;
import org.fleetguard.dto.TireBrandComparisonDto;
import org.fleetguard.dto.TireDto;
import org.fleetguard.dto.TireLayoutRequest;
import org.fleetguard.dto.TireRequest;
import org.fleetguard.dto.TreadReadingInput;
import org.fleetguard.dto.TreadReadingsRequest;
import org.fleetguard.dto.UnmountTireRequest;
import org.fleetguard.dto.VehicleTiresDto;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Tire;
import org.fleetguard.entity.TireMounting;
import org.fleetguard.entity.TireTreadReading;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.event.TireTreadLowEvent;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.TireMountingRepository;
import org.fleetguard.repository.TireRepository;
import org.fleetguard.repository.TireTreadReadingRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// CAM-157: cubiertas por posición, km recorridos, rotación, dibujo y costo por km por marca.
class TireServiceTest {

    /** Un reloj que avanza un minuto en cada lectura, para que los montajes queden en orden. */
    private static final class StepClock extends Clock {
        private Instant now = Instant.parse("2026-11-01T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            now = now.plusSeconds(60);
            return now;
        }
    }

    private final TireRepository tireRepository = mock(TireRepository.class);
    private final TireMountingRepository mountingRepository = mock(TireMountingRepository.class);
    private final TireTreadReadingRepository readingRepository = mock(TireTreadReadingRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final List<Object> events = new ArrayList<>();
    private final TireService service = new TireService(tireRepository, mountingRepository, readingRepository,
            vehicleRepository, events::add, new StepClock(), 1.6);

    private final List<Tire> tires = new ArrayList<>();
    private final List<TireMounting> mountings = new ArrayList<>();
    private final List<TireTreadReading> readings = new ArrayList<>();
    private final AuthenticatedUser driver = new AuthenticatedUser(UUID.randomUUID(), "chofer1", Role.CHOFER);
    private Vehicle truck;

    private static void setId(Object entity) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        if (field.get(entity) == null) {
            field.set(entity, UUID.randomUUID());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        truck = new Vehicle("AB123CD", "Iveco", "Tector");
        setId(truck);
        truck.setOdometerKm(100_000);
        when(vehicleRepository.findById(truck.getId())).thenReturn(java.util.Optional.of(truck));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        when(tireRepository.save(any(Tire.class))).thenAnswer(inv -> {
            Tire tire = inv.getArgument(0);
            if (tire.getId() == null) {
                setId(tire);
                tires.add(tire);
            }
            return tire;
        });
        when(tireRepository.findById(any())).thenAnswer(inv -> tires.stream()
                .filter(t -> t.getId().equals(inv.getArgument(0))).findFirst());
        when(tireRepository.findAllByOrderByBrandAscCreatedAtAsc()).thenAnswer(inv -> List.copyOf(tires));

        when(mountingRepository.save(any(TireMounting.class))).thenAnswer(inv -> {
            TireMounting mounting = inv.getArgument(0);
            if (mounting.getId() == null) {
                setId(mounting);
                mountings.add(mounting);
            }
            return mounting;
        });
        when(mountingRepository.findAllWithTireAndVehicle()).thenAnswer(inv -> List.copyOf(mountings));
        when(mountingRepository.findByVehicle_IdAndRemovedAtIsNull(any())).thenAnswer(inv -> mountings.stream()
                .filter(m -> m.getVehicle().getId().equals(inv.getArgument(0)) && m.isOpen()).toList());
        when(mountingRepository.findByVehicle_IdOrderByMountedAtDesc(any())).thenAnswer(inv -> mountings.stream()
                .filter(m -> m.getVehicle().getId().equals(inv.getArgument(0)))
                .sorted(Comparator.comparing(TireMounting::getMountedAt).reversed()).toList());
        when(mountingRepository.findByTire_IdOrderByMountedAtAsc(any())).thenAnswer(inv -> mountings.stream()
                .filter(m -> m.getTire().getId().equals(inv.getArgument(0))).toList());
        when(mountingRepository.existsByTire_Id(any())).thenAnswer(inv -> mountings.stream()
                .anyMatch(m -> m.getTire().getId().equals(inv.getArgument(0))));

        when(readingRepository.save(any(TireTreadReading.class))).thenAnswer(inv -> {
            TireTreadReading reading = inv.getArgument(0);
            setId(reading);
            readings.add(0, reading);
            return reading;
        });
        when(readingRepository.findAllByOrderByMeasuredAtDesc()).thenAnswer(inv -> List.copyOf(readings));
    }

    private TireDto tire(String brand, String cost) {
        return service.create(new TireRequest(null, brand, null, "295/80 R22.5", "2026-10-01", new BigDecimal(cost), null));
    }

    private VehicleTiresDto mount(TireDto tire, String position) {
        return service.mount(truck.getId().toString(), new MountTireRequest(tire.id(), position, null, null));
    }

    private static VehicleTiresDto.Position at(VehicleTiresDto dto, String position) {
        return dto.positions().stream().filter(p -> p.position().equals(position)).findFirst().orElseThrow();
    }

    private void layout(String layout) {
        service.setLayout(truck.getId().toString(), new TireLayoutRequest(layout));
    }

    @Test
    void elVehiculoMuestraQueCubiertaHayEnCadaPosicionYCuantosKmLleva() {
        layout("6_ruedas");
        TireDto michelin = tire("Michelin", "450000");
        mount(michelin, "del-izq");
        truck.setOdometerKm(101_500);

        VehicleTiresDto dto = service.vehicleTires(truck.getId().toString());

        assertEquals("6_ruedas", dto.layout());
        assertEquals(7, dto.positions().size());
        assertEquals("Delantera izquierda", at(dto, "del-izq").label());
        assertEquals("Trasera derecha interior", at(dto, "e2-der-int").label());
        assertEquals(1500, at(dto, "del-izq").tire().km());
        assertEquals("mounted", at(dto, "del-izq").tire().status());
        assertEquals(new BigDecimal("300.00"), at(dto, "del-izq").tire().costPerKm());
        assertNull(at(dto, "del-der").tire());
    }

    @Test
    void alRotarSusKmSeSiguenSumandoEnLaNuevaPosicion() {
        layout("4_ruedas");
        TireDto a = tire("Michelin", "100000");
        TireDto b = tire("Pirelli", "100000");
        mount(a, "del-izq");
        mount(b, "tras-der");
        truck.setOdometerKm(101_000);

        service.rotate(truck.getId().toString(), new RotateTiresRequest("del-izq", "tras-der"));
        truck.setOdometerKm(102_000);
        VehicleTiresDto dto = service.vehicleTires(truck.getId().toString());

        assertEquals(a.id(), at(dto, "tras-der").tire().id());
        assertEquals(b.id(), at(dto, "del-izq").tire().id());
        assertEquals(2000, at(dto, "tras-der").tire().km());
        assertEquals(2000, at(dto, "del-izq").tire().km());
        // Queda el historial de dónde estuvo cada una.
        assertEquals(4, dto.history().size());
        assertEquals(2, service.get(a.id()).mountings().size());
    }

    @Test
    void elAuxilioNoSumaKm() {
        layout("4_ruedas");
        TireDto spare = tire("Fate", "80000");
        mount(spare, "auxilio");
        truck.setOdometerKm(105_000);

        assertEquals(0, at(service.vehicleTires(truck.getId().toString()), "auxilio").tire().km());
    }

    @Test
    void cambiarUnaCubiertaMandaLaAnteriorAlStockOLaDaDeBaja() {
        layout("4_ruedas");
        TireDto old = tire("Fate", "80000");
        TireDto fresh = tire("Michelin", "120000");
        mount(old, "del-izq");

        VehicleStateConflictException occupied = assertThrows(VehicleStateConflictException.class,
                () -> mount(fresh, "del-izq"));
        assertEquals("POSITION_OCCUPIED", occupied.getErrorCode());

        VehicleTiresDto dto = service.mount(truck.getId().toString(),
                new MountTireRequest(fresh.id(), "del-izq", "retired", "Corte lateral"));

        assertEquals(fresh.id(), at(dto, "del-izq").tire().id());
        TireDto retired = service.get(old.id()).tire();
        assertEquals("retired", retired.status());
        assertEquals("Corte lateral", retired.retiredReason());
        assertEquals("TIRE_NOT_IN_STOCK", assertThrows(VehicleStateConflictException.class,
                () -> mount(old, "del-der")).getErrorCode());
    }

    @Test
    void sacarUnaCubiertaLaDevuelveAlStock() {
        layout("4_ruedas");
        TireDto tire = tire("Fate", "80000");
        mount(tire, "del-izq");

        service.unmount(truck.getId().toString(), new UnmountTireRequest("del-izq", false, null));

        assertEquals("stock", service.get(tire.id()).tire().status());
        assertEquals("POSITION_EMPTY", assertThrows(VehicleStateConflictException.class,
                () -> service.unmount(truck.getId().toString(), new UnmountTireRequest("del-izq", false, null)))
                .getErrorCode());
    }

    @Test
    void sinConfiguracionDeEjesNoSeMontaYUnaPosicionAjenaSeRechaza() {
        TireDto tire = tire("Fate", "80000");
        assertEquals("NO_TIRE_LAYOUT", assertThrows(VehicleStateConflictException.class,
                () -> mount(tire, "del-izq")).getErrorCode());

        layout("4_ruedas");
        assertThrows(VehicleValidationException.class, () -> mount(tire, "e3-izq-int"));
    }

    @Test
    void noSeCambiaLaConfiguracionSiQuedanCubiertasEnPosicionesQueDesaparecen() {
        layout("10_ruedas");
        mount(tire("Fate", "80000"), "e3-izq-ext");

        VehicleStateConflictException ex = assertThrows(VehicleStateConflictException.class, () -> layout("4_ruedas"));
        assertEquals("TIRES_ON_REMOVED_POSITIONS", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Eje 3 izquierda exterior"));
    }

    @Test
    void unDibujoPorDebajoDelMinimoAvisaUnaSolaVezHastaQueSeMideBien() {
        layout("4_ruedas");
        TireDto tire = tire("Fate", "80000");
        mount(tire, "del-izq");
        String vehicleId = truck.getId().toString();

        service.recordAdminReadings(vehicleId, new TreadReadingsRequest(
                List.of(new TreadReadingInput("del-izq", new BigDecimal("1.4")))), null);
        service.recordAdminReadings(vehicleId, new TreadReadingsRequest(
                List.of(new TreadReadingInput("del-izq", new BigDecimal("1.3")))), null);

        assertEquals(1, events.size());
        TireTreadLowEvent event = (TireTreadLowEvent) events.get(0);
        assertEquals("Delantera izquierda", event.positionLabel());
        assertEquals(new BigDecimal("1.4"), event.depthMm());
        assertTrue(service.get(tire.id()).tire().lastTread().belowMinimum());

        service.recordAdminReadings(vehicleId, new TreadReadingsRequest(
                List.of(new TreadReadingInput("del-izq", new BigDecimal("8.0")))), null);
        service.recordAdminReadings(vehicleId, new TreadReadingsRequest(
                List.of(new TreadReadingInput("del-izq", new BigDecimal("1.5")))), null);
        assertEquals(2, events.size());
    }

    @Test
    void elDibujoDelPreViajeSeValidaContraLasCubiertasMontadas() {
        layout("4_ruedas");
        mount(tire("Fate", "80000"), "del-izq");

        InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                () -> service.validateInspectionReadings(truck, List.of(
                        new TreadReadingInput("del-der", new BigDecimal("5")),
                        new TreadReadingInput("del-izq", new BigDecimal("5.55")))));
        assertEquals(List.of("treadReadings[0].position", "treadReadings[1].depthMm"),
                ex.getDetails().stream().map(d -> d.itemId()).toList());

        List<TireService.Reading> valid = service.validateInspectionReadings(truck,
                List.of(new TreadReadingInput("del-izq", new BigDecimal("1.0"))));
        service.recordInspectionReadings(truck, valid, Instant.parse("2026-11-02T08:00:00Z"), driver, UUID.randomUUID());
        assertEquals("chofer1", readings.get(0).getMeasuredByName());
        assertEquals("chofer1", ((TireTreadLowEvent) events.get(0)).measuredBy());
        assertTrue(service.validateInspectionReadings(truck, null).isEmpty());
    }

    @Test
    void elChoferVeLasPosicionesQueRuedanYTienenCubierta() {
        layout("4_ruedas");
        mount(tire("Fate", "80000"), "del-izq");
        mount(tire("Fate", "80000"), "auxilio");

        assertEquals(List.of("del-izq"), service.mountedPositions(truck.getId().toString()).stream()
                .map(p -> p.position()).toList());
    }

    @Test
    void comparaElCostoPorKmEntreMarcas() {
        layout("4_ruedas");
        mount(tire("Michelin", "400000"), "del-izq");
        mount(tire("Michelin", "400000"), "del-der");
        mount(tire("Fate", "200000"), "tras-izq");
        // Con menos de 1.000 km todavía no cuenta.
        TireDto recent = tire("Pirelli", "300000");
        truck.setOdometerKm(110_000);
        service.mount(truck.getId().toString(), new MountTireRequest(recent.id(), "tras-der", null, null));
        truck.setOdometerKm(110_500);

        TireBrandComparisonDto comparison = service.brandComparison();

        // Del más barato por km al más caro.
        assertEquals(List.of("Fate", "Michelin"), comparison.brands().stream().map(b -> b.brand()).toList());
        assertEquals(new BigDecimal("19.05"), comparison.brands().get(0).costPerKm());
        assertEquals(new BigDecimal("38.10"), comparison.brands().get(1).costPerKm());
        assertEquals(2, comparison.brands().get(1).tires());
        assertEquals(21_000, comparison.brands().get(1).totalKm());
        assertEquals(10_500, comparison.brands().get(1).averageKm());
    }

    @Test
    void unaCubiertaMontadaNoSeDaDeBajaNiUnaConHistoriaSeBorra() {
        layout("4_ruedas");
        TireDto tire = tire("Fate", "80000");
        mount(tire, "del-izq");

        assertEquals("TIRE_MOUNTED", assertThrows(VehicleStateConflictException.class,
                () -> service.retire(tire.id(), new RetireTireRequest("Gastada"))).getErrorCode());
        assertEquals("TIRE_HAS_HISTORY", assertThrows(VehicleStateConflictException.class,
                () -> service.delete(tire.id())).getErrorCode());
    }

    @Test
    void validaLosDatosDeLaCubierta() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class, () -> service.create(
                new TireRequest(null, " ", null, null, "2030-01-01", BigDecimal.ZERO, new BigDecimal("40"))));
        assertEquals(List.of("brand", "size", "purchaseDate", "cost", "initialTreadMm"),
                ex.getDetails().stream().map(d -> d.field()).toList());
    }
}
