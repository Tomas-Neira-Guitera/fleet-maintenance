package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.MountTireRequest;
import org.fleetguard.dto.RetireTireRequest;
import org.fleetguard.dto.RotateTiresRequest;
import org.fleetguard.dto.TireBrandComparisonDto;
import org.fleetguard.dto.TireDto;
import org.fleetguard.dto.TireRequest;
import org.fleetguard.dto.TireConditionInput;
import org.fleetguard.dto.UnmountTireRequest;
import org.fleetguard.dto.VehicleTiresDto;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.Tire;
import org.fleetguard.entity.TireMounting;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.TireConditionReport;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleType;
import org.fleetguard.event.TireProblemReportedEvent;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.TireMountingRepository;
import org.fleetguard.repository.TireRepository;
import org.fleetguard.repository.TireConditionReportRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private final TireConditionReportRepository reportRepository = mock(TireConditionReportRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final List<Object> events = new ArrayList<>();
    private final TireService service = new TireService(tireRepository, mountingRepository, reportRepository,
            vehicleRepository, events::add, new StepClock());

    private final List<Tire> tires = new ArrayList<>();
    private final List<TireMounting> mountings = new ArrayList<>();
    private final List<TireConditionReport> reports = new ArrayList<>();
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

        when(reportRepository.save(any(TireConditionReport.class))).thenAnswer(inv -> {
            TireConditionReport report = inv.getArgument(0);
            setId(report);
            reports.add(0, report);
            return report;
        });
        when(reportRepository.findAllWithTireAndDefect()).thenAnswer(inv -> List.copyOf(reports));
        when(reportRepository.findByTireWithDefect(any())).thenAnswer(inv -> reports.stream()
                .filter(r -> r.getTire().getId().equals(inv.getArgument(0))).toList());
    }

    private TireDto tire(String brand, String cost) {
        return service.create(new TireRequest(null, brand, null, "295/80 R22.5", "2026-10-01", new BigDecimal(cost)));
    }

    private VehicleTiresDto mount(TireDto tire, String position) {
        return service.mount(truck.getId().toString(), new MountTireRequest(tire.id(), position, null, null));
    }

    private static VehicleTiresDto.Position at(VehicleTiresDto dto, String position) {
        return dto.positions().stream().filter(p -> p.position().equals(position)).findFirst().orElseThrow();
    }

    private void type(VehicleType type) {
        truck.setVehicleType(type);
    }

    @Test
    void elVehiculoMuestraQueCubiertaHayEnCadaPosicionYCuantosKmLleva() {
        type(VehicleType.TRACTOR_4X2);
        TireDto michelin = tire("Michelin", "450000");
        mount(michelin, "e1-izq");
        truck.setOdometerKm(101_500);

        VehicleTiresDto dto = service.vehicleTires(truck.getId().toString());

        assertEquals("tractor_4x2", dto.vehicleType());
        assertEquals(7, dto.positions().size());
        assertEquals("Delantera izquierda", at(dto, "e1-izq").label());
        assertEquals("Trasera derecha interior", at(dto, "e2-der-int").label());
        assertEquals(1500, at(dto, "e1-izq").tire().km());
        assertEquals("mounted", at(dto, "e1-izq").tire().status());
        assertEquals(new BigDecimal("300.00"), at(dto, "e1-izq").tire().costPerKm());
        assertNull(at(dto, "e1-der").tire());
    }

    @Test
    void alRotarSusKmSeSiguenSumandoEnLaNuevaPosicion() {
        type(VehicleType.UTILITARIO);
        TireDto a = tire("Michelin", "100000");
        TireDto b = tire("Pirelli", "100000");
        mount(a, "e1-izq");
        mount(b, "e2-der");
        truck.setOdometerKm(101_000);

        service.rotate(truck.getId().toString(), new RotateTiresRequest("e1-izq", "e2-der"));
        truck.setOdometerKm(102_000);
        VehicleTiresDto dto = service.vehicleTires(truck.getId().toString());

        assertEquals(a.id(), at(dto, "e2-der").tire().id());
        assertEquals(b.id(), at(dto, "e1-izq").tire().id());
        assertEquals(2000, at(dto, "e2-der").tire().km());
        assertEquals(2000, at(dto, "e1-izq").tire().km());
        // Queda el historial de dónde estuvo cada una.
        assertEquals(4, dto.history().size());
        assertEquals(2, service.get(a.id()).mountings().size());
    }

    @Test
    void elAuxilioNoSumaKm() {
        type(VehicleType.UTILITARIO);
        TireDto spare = tire("Fate", "80000");
        mount(spare, "auxilio");
        truck.setOdometerKm(105_000);

        assertEquals(0, at(service.vehicleTires(truck.getId().toString()), "auxilio").tire().km());
    }

    @Test
    void cambiarUnaCubiertaMandaLaAnteriorAlStockOLaDaDeBaja() {
        type(VehicleType.UTILITARIO);
        TireDto old = tire("Fate", "80000");
        TireDto fresh = tire("Michelin", "120000");
        mount(old, "e1-izq");

        VehicleStateConflictException occupied = assertThrows(VehicleStateConflictException.class,
                () -> mount(fresh, "e1-izq"));
        assertEquals("POSITION_OCCUPIED", occupied.getErrorCode());

        VehicleTiresDto dto = service.mount(truck.getId().toString(),
                new MountTireRequest(fresh.id(), "e1-izq", "retired", "Corte lateral"));

        assertEquals(fresh.id(), at(dto, "e1-izq").tire().id());
        TireDto retired = service.get(old.id()).tire();
        assertEquals("retired", retired.status());
        assertEquals("Corte lateral", retired.retiredReason());
        assertEquals("TIRE_NOT_IN_STOCK", assertThrows(VehicleStateConflictException.class,
                () -> mount(old, "e1-der")).getErrorCode());
    }

    @Test
    void sacarUnaCubiertaLaDevuelveAlStock() {
        type(VehicleType.UTILITARIO);
        TireDto tire = tire("Fate", "80000");
        mount(tire, "e1-izq");

        service.unmount(truck.getId().toString(), new UnmountTireRequest("e1-izq", false, null));

        assertEquals("stock", service.get(tire.id()).tire().status());
        assertEquals("POSITION_EMPTY", assertThrows(VehicleStateConflictException.class,
                () -> service.unmount(truck.getId().toString(), new UnmountTireRequest("e1-izq", false, null)))
                .getErrorCode());
    }

    @Test
    void sinTipoNoSeMontaYUnaPosicionQueElTipoNoTieneSeRechaza() {
        TireDto tire = tire("Fate", "80000");
        truck.setVehicleType(null);
        assertEquals("NO_VEHICLE_TYPE", assertThrows(VehicleStateConflictException.class,
                () -> mount(tire, "e1-izq")).getErrorCode());

        type(VehicleType.UTILITARIO);
        assertThrows(VehicleValidationException.class, () -> mount(tire, "e2-izq-int"));
    }

    private static TireConditionInput report(String position, String... conditions) {
        return new TireConditionInput(position, List.of(conditions));
    }

    @Test
    void lasCubiertasConProblemasSeValidanContraLasMontadas() {
        type(VehicleType.UTILITARIO);
        mount(tire("Fate", "80000"), "e1-izq");
        mount(tire("Fate", "80000"), "e1-der");

        InspectionValidationException ex = assertThrows(InspectionValidationException.class,
                () -> service.validateInspectionConditions(truck, List.of(report("e2-der", "pinchada"),
                        report("e1-izq", "volcada"), report("e1-der"), report("e1-izq", "pinchada")), true));
        assertEquals(List.of("tireConditions[0].position", "tireConditions[1].conditions",
                        "tireConditions[2].conditions", "tireConditions[3].position"),
                ex.getDetails().stream().map(d -> d.itemId()).toList());

        // Sin "Neumáticos" en defecto no se aceptan; sin cubiertas marcadas, vale.
        assertEquals("tireConditions", assertThrows(InspectionValidationException.class,
                () -> service.validateInspectionConditions(truck, List.of(report("e1-izq", "pinchada")), false))
                .getDetails().get(0).itemId());
        assertTrue(service.validateInspectionConditions(truck, null, true).isEmpty());
    }

    @Test
    void elReporteQuedaEnLaCubiertaMientrasElDefectoSigaAbiertoYAvisa() throws Exception {
        type(VehicleType.CAMION_4X2);
        TireDto rear = tire("Michelin", "500000");
        mount(rear, "e2-der-ext");
        List<TireService.ConditionReport> valid = service.validateInspectionConditions(truck,
                List.of(report("e2-der-ext", "se-ve-la-tela", "poco-dibujo")), true);
        assertEquals("Cubiertas: Trasera derecha exterior: se ve la tela o el alambre, poco dibujo.",
                service.summary(truck, valid));

        Defect defect = new Defect(DefectSeverity.BLOCKING, "Neumático trasero", null, Instant.now());
        setId(defect);
        service.recordInspectionConditions(truck, valid, Instant.parse("2026-11-02T08:00:00Z"), driver,
                UUID.randomUUID(), defect);

        TireProblemReportedEvent event = (TireProblemReportedEvent) events.get(0);
        assertEquals("chofer1", event.reportedBy());
        assertEquals(List.of("Trasera derecha exterior (Michelin): se ve la tela o el alambre, poco dibujo"), event.tires());
        var condition = service.get(rear.id()).tire().condition();
        assertEquals(List.of("se-ve-la-tela", "poco-dibujo"), condition.conditions());
        assertEquals("blocking", condition.severity());
        assertEquals("chofer1", condition.reportedBy());
        assertEquals("Trasera derecha exterior", at(service.vehicleTires(truck.getId().toString()), "e2-der-ext")
                .tire().condition().positionLabel());

        // Resuelto el defecto, la cubierta vuelve a estar sin problemas; el reporte queda en su historial.
        defect.resolve(Instant.now());
        assertNull(service.get(rear.id()).tire().condition());
        assertFalse(service.get(rear.id()).reports().get(0).active());
    }

    @Test
    void elChoferVeTodasLasPosicionesYCualesTienenCubierta() {
        type(VehicleType.UTILITARIO);
        mount(tire("Fate", "80000"), "e1-izq");
        mount(tire("Fate", "80000"), "auxilio");

        var positions = service.positions(truck.getId().toString());
        assertEquals("utilitario", positions.vehicleType());
        assertEquals(List.of("e1-izq", "e1-der", "e2-izq", "e2-der", "auxilio"),
                positions.positions().stream().map(p -> p.position()).toList());
        assertEquals(List.of(true, false, false, false, true),
                positions.positions().stream().map(p -> p.mounted()).toList());
        type(VehicleType.ACOPLADO);
        assertTrue(service.positions(truck.getId().toString()).positions().isEmpty());
    }

    @Test
    void comparaElCostoPorKmEntreMarcas() {
        type(VehicleType.UTILITARIO);
        mount(tire("Michelin", "400000"), "e1-izq");
        mount(tire("Michelin", "400000"), "e1-der");
        mount(tire("Fate", "200000"), "e2-izq");
        // Con menos de 1.000 km todavía no cuenta.
        TireDto recent = tire("Pirelli", "300000");
        truck.setOdometerKm(110_000);
        service.mount(truck.getId().toString(), new MountTireRequest(recent.id(), "e2-der", null, null));
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
        type(VehicleType.UTILITARIO);
        TireDto tire = tire("Fate", "80000");
        mount(tire, "e1-izq");

        assertEquals("TIRE_MOUNTED", assertThrows(VehicleStateConflictException.class,
                () -> service.retire(tire.id(), new RetireTireRequest("Gastada"))).getErrorCode());
        assertEquals("TIRE_HAS_HISTORY", assertThrows(VehicleStateConflictException.class,
                () -> service.delete(tire.id())).getErrorCode());
    }

    @Test
    void validaLosDatosDeLaCubierta() {
        VehicleValidationException ex = assertThrows(VehicleValidationException.class, () -> service.create(
                new TireRequest(null, " ", null, null, "2030-01-01", BigDecimal.ZERO)));
        assertEquals(List.of("brand", "size", "purchaseDate", "cost"),
                ex.getDetails().stream().map(d -> d.field()).toList());
    }
}
