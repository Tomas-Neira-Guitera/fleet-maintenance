package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.FuelLoadDto;
import org.fleetguard.dto.FuelLoadRequest;
import org.fleetguard.dto.VehicleFuelDto;
import org.fleetguard.entity.FuelLoad;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.event.FuelConsumptionAnomalyEvent;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.FuelLoadRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// CAM-156: cargas de combustible, consumo por tanque lleno y aviso de consumo anormal.
class FuelLoadServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");

    private final FuelLoadRepository fuelLoadRepository = mock(FuelLoadRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final List<Object> events = new ArrayList<>();
    private final FuelLoadService service = new FuelLoadService(fuelLoadRepository, vehicleRepository,
            new OdometerReadings(5000), new UserNameService(mock(UserRepository.class)), events::add,
            Clock.fixed(NOW, ZoneOffset.UTC), 3, 10, 1.3);

    private final List<FuelLoad> loads = new ArrayList<>();
    private final AuthenticatedUser driver = new AuthenticatedUser(UUID.randomUUID(), "chofer1", Role.CHOFER);
    private Vehicle truck;

    private static void setField(Object entity, String name, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        truck = new Vehicle("AB123CD", "Iveco", "Tector");
        setField(truck, "id", UUID.randomUUID());
        truck.setOdometerKm(100_000);
        when(vehicleRepository.findById(truck.getId())).thenReturn(Optional.of(truck));
        when(fuelLoadRepository.findByVehicle_IdOrderByLoadedAtAscOdometerKmAsc(any())).thenAnswer(inv -> loads.stream()
                .sorted(Comparator.comparing(FuelLoad::getLoadedAt).thenComparingLong(FuelLoad::getOdometerKm))
                .toList());
        when(fuelLoadRepository.findById(any())).thenAnswer(inv -> loads.stream()
                .filter(l -> l.getId().equals(inv.getArgument(0))).findFirst());
        when(fuelLoadRepository.save(any(FuelLoad.class))).thenAnswer(inv -> {
            FuelLoad load = inv.getArgument(0);
            if (load.getId() == null) {
                setField(load, "id", UUID.randomUUID());
                loads.add(load);
            }
            return load;
        });
    }

    private FuelLoadRequest request(String at, String liters, String amount, long km, Boolean fullTank) {
        return new FuelLoadRequest(truck.getId().toString(), at, new BigDecimal(liters), new BigDecimal(amount), km, fullTank);
    }

    private FuelLoadDto load(String at, String liters, long km) {
        return service.create(request(at, liters, "1000", km, true), driver);
    }

    @Test
    void conDosCargasDeTanqueLlenoCalculaElConsumo() {
        FuelLoadDto first = service.create(request("2026-11-01T10:00:00Z", "100", "120000", 100_000, true), driver);
        FuelLoadDto second = service.create(request("2026-11-03T10:00:00Z", "150", "180000", 100_500, null), driver);

        assertNull(first.consumption());
        assertEquals(500, second.consumption().km());
        assertEquals(new BigDecimal("30.00"), second.consumption().litersPer100Km());
        assertEquals(new BigDecimal("360.00"), second.consumption().costPerKm());
        assertTrue(second.fullTank());
        assertEquals(driver.id().toString(), second.createdBy().id());
        // El km de la carga mueve el odómetro del vehículo.
        assertEquals(100_500, truck.getOdometerKm());
    }

    @Test
    void lasCargasParcialesSeSumanAlTramoHastaElProximoTanqueLleno() {
        load("2026-11-01T10:00:00Z", "100", 100_000);
        service.create(request("2026-11-02T10:00:00Z", "40", "1000", 100_200, false), driver);
        FuelLoadDto closing = load("2026-11-03T10:00:00Z", "60", 100_500);

        assertEquals(new BigDecimal("100"), closing.consumption().liters());
        assertEquals(new BigDecimal("20.00"), closing.consumption().litersPer100Km());
    }

    @Test
    void unaCargaConMenosKmQueLaAnteriorSeRechaza() {
        load("2026-11-01T10:00:00Z", "100", 100_000);

        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> load("2026-11-02T10:00:00Z", "50", 99_900));

        assertEquals("odometerKm", ex.getDetails().get(0).field());
        assertTrue(ex.getDetails().get(0).message().contains("100.000 km"));
        assertEquals(1, loads.size());
    }

    @Test
    void unaCargaAnteriorNoPuedeTenerMasKmQueLaSiguiente() {
        load("2026-11-05T10:00:00Z", "100", 100_000);

        VehicleValidationException ex = assertThrows(VehicleValidationException.class,
                () -> load("2026-11-01T10:00:00Z", "50", 100_100));

        assertEquals("odometerKm", ex.getDetails().get(0).field());
    }

    @Test
    void unConsumoMuyPorEncimaDelPromedioAvisa() {
        load("2026-11-01T10:00:00Z", "100", 100_000);
        load("2026-11-02T10:00:00Z", "150", 100_500);
        load("2026-11-03T10:00:00Z", "150", 101_000);
        FuelLoadDto normal = load("2026-11-04T10:00:00Z", "160", 101_500);
        assertNull(normal.alert());
        assertTrue(events.isEmpty());

        // 45 L cada 100 km contra un promedio de 30,67: más de un 30% arriba.
        FuelLoadDto high = load("2026-11-05T10:00:00Z", "225", 102_000);

        assertNotNull(high.alert());
        assertEquals(new BigDecimal("45.00"), high.alert().litersPer100Km());
        assertEquals(new BigDecimal("30.67"), high.alert().averageLitersPer100Km());
        FuelConsumptionAnomalyEvent event = (FuelConsumptionAnomalyEvent) events.get(0);
        assertEquals("AB123CD", event.plate());
        assertEquals(500, event.km());
    }

    @Test
    void sinTresTramosAnterioresNoHayPromedioNiAviso() {
        load("2026-11-01T10:00:00Z", "100", 100_000);
        load("2026-11-02T10:00:00Z", "150", 100_500);
        FuelLoadDto third = load("2026-11-03T10:00:00Z", "600", 101_000);

        assertNull(third.alert());
        assertTrue(events.isEmpty());
    }

    @Test
    void validaLosDatosYElVehiculo() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class, () -> service.create(
                new FuelLoadRequest(truck.getId().toString(), "2026-11-11T10:00:00Z", BigDecimal.ZERO,
                        new BigDecimal("10.123"), null, true), driver));
        assertEquals(List.of("loadedAt", "liters", "amount", "odometerKm"),
                ex.getDetails().stream().map(d -> d.field()).toList());

        truck.setActive(false);
        VehicleStateConflictException inactive = assertThrows(VehicleStateConflictException.class,
                () -> load("2026-11-01T10:00:00Z", "100", 100_000));
        assertEquals("VEHICLE_INACTIVE", inactive.getErrorCode());
    }

    @Test
    void sinFechaEsAhora() {
        FuelLoadDto created = service.create(request(null, "100", "1000", 100_000, true), driver);
        assertEquals(NOW.toString(), created.loadedAt());
    }

    @Test
    void editarRecalculaElTramoPeroNoVuelveAAvisar() {
        load("2026-11-01T10:00:00Z", "100", 100_000);
        FuelLoadDto second = load("2026-11-03T10:00:00Z", "150", 100_500);

        FuelLoadDto edited = service.update(second.id(), request("2026-11-03T10:00:00Z", "100", "1000", 100_500, true));

        assertEquals(new BigDecimal("20.00"), edited.consumption().litersPer100Km());
        assertTrue(events.isEmpty());
    }

    @Test
    void elConsumoDelVehiculoSaleMesPorMes() {
        load("2026-09-28T10:00:00Z", "100", 100_000);
        load("2026-10-10T10:00:00Z", "120", 100_400);
        load("2026-11-02T10:00:00Z", "100", 100_900);

        VehicleFuelDto fuel = service.vehicleFuel(truck.getId().toString(), 2);

        assertEquals(2, fuel.monthly().size());
        assertEquals("2026-10", fuel.monthly().get(0).month());
        assertEquals(new BigDecimal("30.00"), fuel.monthly().get(0).litersPer100Km());
        assertEquals(new BigDecimal("20.00"), fuel.monthly().get(1).litersPer100Km());
        // La carga de septiembre queda fuera del período, pero abre el tramo de octubre.
        assertEquals(2, fuel.summary().loadCount());
        assertEquals(900, fuel.summary().measuredKm());
        assertEquals(new BigDecimal("24.44"), fuel.summary().litersPer100Km());
        assertEquals("2026-11-02T10:00:00Z", fuel.loads().get(0).loadedAt());
        assertThrows(VehicleValidationException.class, () -> service.vehicleFuel(truck.getId().toString(), 0));
    }
}
