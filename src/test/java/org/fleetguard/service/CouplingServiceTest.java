package org.fleetguard.service;

import org.fleetguard.dto.CoupleRequest;
import org.fleetguard.dto.CouplingDto;
import org.fleetguard.entity.TrailerCoupling;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleType;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.repository.TrailerCouplingRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// CAM-157: enganche de remolques y los km que suman mientras los arrastran.
class CouplingServiceTest {

    private final TrailerCouplingRepository couplingRepository = mock(TrailerCouplingRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final CouplingService service = new CouplingService(couplingRepository, vehicleRepository,
            Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC));
    private final OdometerReadings odometer = new OdometerReadings(5000, event -> { }, couplingRepository);

    private final List<TrailerCoupling> couplings = new ArrayList<>();
    private final List<Vehicle> vehicles = new ArrayList<>();

    private static void setId(Object entity) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        if (field.get(entity) == null) {
            field.set(entity, UUID.randomUUID());
        }
    }

    private Vehicle vehicle(String plate, VehicleType type, long km) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Marca", "Modelo");
        vehicle.setVehicleType(type);
        vehicle.setOdometerKm(km);
        setId(vehicle);
        vehicles.add(vehicle);
        return vehicle;
    }

    @BeforeEach
    void setUp() {
        when(vehicleRepository.findById(any())).thenAnswer(inv -> vehicles.stream()
                .filter(v -> v.getId().equals(inv.getArgument(0))).findFirst());
        when(couplingRepository.save(any(TrailerCoupling.class))).thenAnswer(inv -> {
            TrailerCoupling coupling = inv.getArgument(0);
            if (coupling.getId() == null) {
                setId(coupling);
                couplings.add(coupling);
            }
            return coupling;
        });
        when(couplingRepository.findFirstByTrailer_IdAndUncoupledAtIsNull(any())).thenAnswer(inv -> couplings.stream()
                .filter(c -> c.isOpen() && c.getTrailer().getId().equals(inv.getArgument(0))).findFirst());
        when(couplingRepository.findFirstByTowing_IdAndUncoupledAtIsNull(any())).thenAnswer(inv -> couplings.stream()
                .filter(c -> c.isOpen() && c.getTowing().getId().equals(inv.getArgument(0))).findFirst());
        when(couplingRepository.findByTrailer_IdOrTowing_IdOrderByCoupledAtDesc(any(), any())).thenAnswer(inv -> couplings.stream()
                .filter(c -> c.getTrailer().getId().equals(inv.getArgument(0)) || c.getTowing().getId().equals(inv.getArgument(1)))
                .sorted(Comparator.comparing(TrailerCoupling::getCoupledAt).reversed()).toList());
    }

    @Test
    void elSemirremolqueSumaLosKmDelTractorMientrasEstaEnganchado() throws Exception {
        Vehicle tractor = vehicle("AB123CD", VehicleType.TRACTOR_6X4, 300_000);
        Vehicle semi = vehicle("ZZ001AA", VehicleType.SEMIRREMOLQUE_3_EJES, 80_000);

        CouplingDto dto = service.couple(semi.getId().toString(), new CoupleRequest(tractor.getId().toString()), "admin");
        assertEquals("AB123CD", dto.towedBy().plate());

        odometer.apply(tractor, 300_450);
        assertEquals(80_450, semi.getOdometerKm());

        service.uncouple(semi.getId().toString());
        odometer.apply(tractor, 301_000);
        assertEquals(80_450, semi.getOdometerKm());
        assertNull(service.get(semi.getId().toString()).towedBy());
        assertEquals(1, service.get(tractor.getId().toString()).history().size());
    }

    @Test
    void unTractorNoArrastraUnAcopladoNiUnUtilitarioUnSemirremolque() throws Exception {
        Vehicle tractor = vehicle("AB123CD", VehicleType.TRACTOR_4X2, 0);
        Vehicle van = vehicle("AC456EF", VehicleType.UTILITARIO, 0);
        Vehicle acoplado = vehicle("ZZ002BB", VehicleType.ACOPLADO, 0);
        Vehicle semi = vehicle("ZZ003CC", VehicleType.SEMIRREMOLQUE_2_EJES, 0);

        assertEquals("CANNOT_TOW", assertThrows(VehicleStateConflictException.class, () -> service.couple(
                acoplado.getId().toString(), new CoupleRequest(tractor.getId().toString()), "admin")).getErrorCode());
        assertEquals("CANNOT_TOW", assertThrows(VehicleStateConflictException.class, () -> service.couple(
                semi.getId().toString(), new CoupleRequest(van.getId().toString()), "admin")).getErrorCode());
        assertEquals("NOT_A_TRAILER", assertThrows(VehicleStateConflictException.class, () -> service.couple(
                van.getId().toString(), new CoupleRequest(tractor.getId().toString()), "admin")).getErrorCode());
    }

    @Test
    void engancharOtroSemirremolqueSueltaElAnterior() throws Exception {
        Vehicle tractor = vehicle("AB123CD", VehicleType.TRACTOR_4X2, 0);
        Vehicle first = vehicle("ZZ001AA", VehicleType.SEMIRREMOLQUE_2_EJES, 0);
        Vehicle second = vehicle("ZZ002BB", VehicleType.SEMIRREMOLQUE_3_EJES, 0);

        service.couple(first.getId().toString(), new CoupleRequest(tractor.getId().toString()), "admin");
        service.couple(second.getId().toString(), new CoupleRequest(tractor.getId().toString()), "admin");

        assertNull(service.get(first.getId().toString()).towedBy());
        assertEquals("ZZ002BB", service.get(tractor.getId().toString()).towing().plate());
        assertTrue(service.get(tractor.getId().toString()).canTow().contains("semirremolque_3_ejes"));
    }

    @Test
    void elPreViajeDelTractorEngancheODesengancha() throws Exception {
        Vehicle tractor = vehicle("AB123CD", VehicleType.TRACTOR_4X2, 0);
        Vehicle semi = vehicle("ZZ001AA", VehicleType.SEMIRREMOLQUE_2_EJES, 0);

        Optional<Vehicle> resolved = service.resolveInspectionTrailer(tractor, semi.getId().toString());
        service.applyInspectionTrailer(tractor, semi.getId().toString(), resolved, "chofer1");
        assertEquals("ZZ001AA", service.get(tractor.getId().toString()).towing().plate());
        assertEquals("chofer1", couplings.get(0).getCoupledByName());

        // Sin el campo, queda como estaba; con "none", va sin remolque.
        service.applyInspectionTrailer(tractor, null, Optional.empty(), "chofer1");
        assertEquals(1, couplings.size());
        service.applyInspectionTrailer(tractor, "none", service.resolveInspectionTrailer(tractor, "none"), "chofer1");
        assertNull(service.get(tractor.getId().toString()).towing());
    }
}
