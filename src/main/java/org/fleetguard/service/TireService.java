package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.MountTireRequest;
import org.fleetguard.dto.RetireTireRequest;
import org.fleetguard.dto.RotateTiresRequest;
import org.fleetguard.dto.TireBrandComparisonDto;
import org.fleetguard.dto.TireDetailDto;
import org.fleetguard.dto.TireDto;
import org.fleetguard.dto.TireLayoutRequest;
import org.fleetguard.dto.TireMountingDto;
import org.fleetguard.dto.TirePositionDto;
import org.fleetguard.dto.TireRequest;
import org.fleetguard.dto.TreadReadingInput;
import org.fleetguard.dto.TreadReadingsRequest;
import org.fleetguard.dto.UnmountTireRequest;
import org.fleetguard.dto.ValidationErrorDetail;
import org.fleetguard.dto.VehicleTiresDto;
import org.fleetguard.entity.Tire;
import org.fleetguard.entity.TireLayout;
import org.fleetguard.entity.TireMounting;
import org.fleetguard.entity.TirePosition;
import org.fleetguard.entity.TireStatus;
import org.fleetguard.entity.TireTreadReading;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.event.TireTreadLowEvent;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.TireNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.TireMountingRepository;
import org.fleetguard.repository.TireRepository;
import org.fleetguard.repository.TireTreadReadingRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-157: cubiertas, dónde está montada cada una, sus km y su dibujo. Ver docs/api/CAM-157-tires-contract.md.
 * Los km de una cubierta salen de sus montajes y del odómetro de cada vehículo: rotarla cierra un montaje y
 * abre otro, así que sigue sumando sin lógica aparte.
 */
@Service
public class TireService {

    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    static final BigDecimal MAX_COST = new BigDecimal("9999999999.99");
    static final BigDecimal MAX_TREAD_MM = new BigDecimal("30");
    // Para comparar marcas: una cubierta con pocos km todavía da un costo por km engañoso.
    static final long COMPARISON_MIN_KM = 1000;

    private final TireRepository tireRepository;
    private final TireMountingRepository mountingRepository;
    private final TireTreadReadingRepository readingRepository;
    private final VehicleRepository vehicleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final BigDecimal minTreadMm;

    public TireService(TireRepository tireRepository, TireMountingRepository mountingRepository,
                       TireTreadReadingRepository readingRepository, VehicleRepository vehicleRepository,
                       ApplicationEventPublisher eventPublisher, Clock clock,
                       @Value("${fleetguard.tires.min-tread-mm:1.6}") double minTreadMm) {
        this.tireRepository = tireRepository;
        this.mountingRepository = mountingRepository;
        this.readingRepository = readingRepository;
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.minTreadMm = BigDecimal.valueOf(minTreadMm).setScale(1, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- cubiertas

    @Transactional(readOnly = true)
    public List<TireDto> list(String statusParam) {
        TireStatus status = statusParam == null || statusParam.isBlank() ? null : TireStatus.fromJson(statusParam);
        if (statusParam != null && !statusParam.isBlank() && status == null) {
            throw invalid("Filtro inválido", "status", "Debe ser 'stock', 'mounted' o 'retired'");
        }
        Snapshot snapshot = snapshot();
        return tireRepository.findAllByOrderByBrandAscCreatedAtAsc().stream()
                .filter(t -> status == null || t.getStatus() == status)
                .map(snapshot::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TireDetailDto get(String id) {
        Tire tire = find(id);
        Snapshot snapshot = snapshot();
        List<TireMountingDto> mountings = mountingRepository.findByTire_IdOrderByMountedAtAsc(tire.getId()).stream()
                .sorted(Comparator.comparing(TireMounting::getMountedAt).reversed())
                .map(this::toMountingDto)
                .toList();
        List<TireDto.Tread> treads = readingRepository.findByTire_IdOrderByMeasuredAtDesc(tire.getId()).stream()
                .map(this::toTread)
                .toList();
        return new TireDetailDto(snapshot.toDto(tire), mountings, treads);
    }

    @Transactional
    public TireDto create(TireRequest request) {
        ValidTire valid = validate(request);
        if (valid.code() != null && tireRepository.existsByCodeIgnoreCase(valid.code())) {
            throw duplicateCode(valid.code());
        }
        Tire tire = tireRepository.save(new Tire(valid.code(), valid.brand(), valid.model(), valid.size(),
                valid.purchaseDate(), valid.cost(), valid.initialTreadMm()));
        return snapshot().toDto(tire);
    }

    @Transactional
    public TireDto update(String id, TireRequest request) {
        Tire tire = find(id);
        ValidTire valid = validate(request);
        if (valid.code() != null && tireRepository.existsByCodeIgnoreCaseAndIdNot(valid.code(), tire.getId())) {
            throw duplicateCode(valid.code());
        }
        tire.update(valid.code(), valid.brand(), valid.model(), valid.size(), valid.purchaseDate(), valid.cost(),
                valid.initialTreadMm());
        return snapshot().toDto(tireRepository.save(tire));
    }

    /** Dar de baja una cubierta del stock. Una montada se da de baja al sacarla del vehículo. */
    @Transactional
    public TireDto retire(String id, RetireTireRequest request) {
        Tire tire = find(id);
        if (tire.getStatus() == TireStatus.MOUNTED) {
            throw new VehicleStateConflictException("TIRE_MOUNTED", "La cubierta está montada: sacala del vehículo para darla de baja");
        }
        if (tire.getStatus() == TireStatus.RETIRED) {
            throw new VehicleStateConflictException("TIRE_RETIRED", "La cubierta ya está dada de baja");
        }
        tire.retire(clock.instant(), reason(request == null ? null : request.reason()));
        return snapshot().toDto(tireRepository.save(tire));
    }

    /** Solo una cargada por error: con montajes ya tiene historia y se da de baja. */
    @Transactional
    public void delete(String id) {
        Tire tire = find(id);
        if (mountingRepository.existsByTire_Id(tire.getId())) {
            throw new VehicleStateConflictException("TIRE_HAS_HISTORY",
                    "La cubierta ya estuvo montada: no se borra, se da de baja");
        }
        tireRepository.delete(tire);
    }

    // ---------------------------------------------------------------- el vehículo

    @Transactional(readOnly = true)
    public VehicleTiresDto vehicleTires(String vehicleId) {
        return toVehicleDto(findVehicle(vehicleId));
    }

    @Transactional
    public VehicleTiresDto setLayout(String vehicleId, TireLayoutRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);
        TireLayout layout = request == null ? null : TireLayout.fromJson(request.layout());
        if (layout == null) {
            throw invalid("Configuración inválida", "layout", "Debe ser '4_ruedas', '6_ruedas' o '10_ruedas'");
        }
        List<String> orphan = mountingRepository.findByVehicle_IdAndRemovedAtIsNull(vehicle.getId()).stream()
                .filter(m -> !layout.has(m.getPosition()))
                .map(m -> labelOf(vehicle, m.getPosition()))
                .toList();
        if (!orphan.isEmpty()) {
            throw new VehicleStateConflictException("TIRES_ON_REMOVED_POSITIONS",
                    "Sacá primero las cubiertas de " + String.join(", ", orphan) + ": esas posiciones no existen en "
                            + layout.label());
        }
        vehicle.setTireLayout(layout);
        vehicleRepository.save(vehicle);
        return toVehicleDto(vehicle);
    }

    @Transactional
    public VehicleTiresDto mount(String vehicleId, MountTireRequest request) {
        Vehicle vehicle = findActiveVehicle(vehicleId);
        if (request == null) {
            throw invalid("Datos inválidos", "tireId", "Obligatorio");
        }
        TirePosition position = requirePosition(vehicle, request.position(), "position");
        Tire tire = find(request.tireId());
        if (tire.getStatus() != TireStatus.STOCK) {
            throw new VehicleStateConflictException("TIRE_NOT_IN_STOCK", tire.getStatus() == TireStatus.RETIRED
                    ? "La cubierta está dada de baja"
                    : "La cubierta ya está montada: sacala o rotala desde su vehículo");
        }
        Instant now = clock.instant();
        Optional<TireMounting> current = openAt(vehicle, position);
        if (current.isPresent()) {
            String replaced = request.replaced();
            if (replaced == null || replaced.isBlank()) {
                throw new VehicleStateConflictException("POSITION_OCCUPIED",
                        labelOf(vehicle, position) + " ya tiene una cubierta: elegí si vuelve al stock o se da de baja");
            }
            if (!replaced.equals("stock") && !replaced.equals("retired")) {
                throw invalid("Datos inválidos", "replaced", "Debe ser 'stock' o 'retired'");
            }
            remove(current.get(), now, replaced.equals("retired"), request.retiredReason());
        }
        tire.mount();
        mountingRepository.save(new TireMounting(tire, vehicle, position, now, vehicle.getOdometerKm()));
        return toVehicleDto(vehicle);
    }

    @Transactional
    public VehicleTiresDto unmount(String vehicleId, UnmountTireRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);
        TirePosition position = requirePosition(vehicle, request == null ? null : request.position(), "position");
        TireMounting current = openAt(vehicle, position).orElseThrow(() ->
                new VehicleStateConflictException("POSITION_EMPTY", labelOf(vehicle, position) + " no tiene cubierta"));
        remove(current, clock.instant(), Boolean.TRUE.equals(request.retire()), request.reason());
        return toVehicleDto(vehicle);
    }

    /** Pasa la cubierta de from a to; si to tiene una, se intercambian. Los km siguen sumando en la nueva posición. */
    @Transactional
    public VehicleTiresDto rotate(String vehicleId, RotateTiresRequest request) {
        Vehicle vehicle = findActiveVehicle(vehicleId);
        TirePosition from = requirePosition(vehicle, request == null ? null : request.from(), "from");
        TirePosition to = requirePosition(vehicle, request.to(), "to");
        if (from == to) {
            throw invalid("Datos inválidos", "to", "Elegí otra posición");
        }
        TireMounting moving = openAt(vehicle, from).orElseThrow(() ->
                new VehicleStateConflictException("POSITION_EMPTY", labelOf(vehicle, from) + " no tiene cubierta"));
        Optional<TireMounting> other = openAt(vehicle, to);
        Instant now = clock.instant();
        long km = vehicle.getOdometerKm();

        moving.close(now, km);
        mountingRepository.save(moving);
        mountingRepository.save(new TireMounting(moving.getTire(), vehicle, to, now, km));
        other.ifPresent(o -> {
            o.close(now, km);
            mountingRepository.save(o);
            mountingRepository.save(new TireMounting(o.getTire(), vehicle, from, now, km));
        });
        return toVehicleDto(vehicle);
    }

    /** Para el pre-viaje del chofer: las posiciones que ruedan y tienen cubierta. */
    @Transactional(readOnly = true)
    public List<TirePositionDto> mountedPositions(String vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        if (vehicle.getTireLayout() == null) {
            return List.of();
        }
        Set<TirePosition> mounted = mountingRepository.findByVehicle_IdAndRemovedAtIsNull(vehicle.getId()).stream()
                .map(TireMounting::getPosition)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TirePosition.class)));
        return vehicle.getTireLayout().positions().stream()
                .filter(TirePosition::rolls)
                .filter(mounted::contains)
                .map(p -> new TirePositionDto(p.toJson(), vehicle.getTireLayout().labelOf(p)))
                .toList();
    }

    // ---------------------------------------------------------------- dibujo

    @Transactional
    public VehicleTiresDto recordAdminReadings(String vehicleId, TreadReadingsRequest request, AuthenticatedUser admin) {
        Vehicle vehicle = findVehicle(vehicleId);
        List<TreadReadingInput> inputs = request == null || request.readings() == null ? List.of() : request.readings();
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (inputs.isEmpty()) {
            details.add(new FieldValidationErrorDetail("readings", "Cargá al menos una medición"));
        }
        List<Reading> readings = parseReadings(vehicle, inputs,
                (field, message) -> details.add(new FieldValidationErrorDetail(field, message)));
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Mediciones inválidas", details);
        }
        record(vehicle, readings, clock.instant(), admin == null ? null : admin.id(),
                admin == null ? null : admin.username(), null);
        return toVehicleDto(vehicle);
    }

    /** Una medición ya validada: la cubierta que está en esa posición. */
    public record Reading(TireMounting mounting, BigDecimal depthMm) {
    }

    /** CAM-157: valida el dibujo que mandó el chofer en el pre-viaje, antes de guardar la inspección. */
    public List<Reading> validateInspectionReadings(Vehicle vehicle, List<TreadReadingInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<ValidationErrorDetail> details = new ArrayList<>();
        List<Reading> readings = parseReadings(vehicle, inputs,
                (field, message) -> details.add(new ValidationErrorDetail(field, message)));
        if (!details.isEmpty()) {
            throw new InspectionValidationException("El dibujo de las cubiertas no es válido.", details);
        }
        return readings;
    }

    @Transactional
    public void recordInspectionReadings(Vehicle vehicle, List<Reading> readings, Instant at, AuthenticatedUser driver,
                                         UUID inspectionId) {
        if (readings.isEmpty()) {
            return;
        }
        record(vehicle, readings, at, driver == null ? null : driver.id(), driver == null ? null : driver.username(),
                inspectionId);
    }

    // ---------------------------------------------------------------- comparación

    @Transactional(readOnly = true)
    public TireBrandComparisonDto brandComparison() {
        Snapshot snapshot = snapshot();
        Map<String, List<Tire>> byBrand = new LinkedHashMap<>();
        for (Tire tire : tireRepository.findAllByOrderByBrandAscCreatedAtAsc()) {
            if (snapshot.km(tire) >= COMPARISON_MIN_KM) {
                byBrand.computeIfAbsent(tire.getBrand().trim(), b -> new ArrayList<>()).add(tire);
            }
        }
        List<TireBrandComparisonDto.Brand> brands = byBrand.entrySet().stream().map(entry -> {
            List<Tire> tires = entry.getValue();
            long km = tires.stream().mapToLong(snapshot::km).sum();
            BigDecimal cost = tires.stream().map(Tire::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            int retired = (int) tires.stream().filter(t -> t.getStatus() == TireStatus.RETIRED).count();
            return new TireBrandComparisonDto.Brand(entry.getKey(), tires.size(), retired, km, cost,
                    cost.divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP), km / tires.size());
        }).sorted(Comparator.comparing(TireBrandComparisonDto.Brand::costPerKm)).toList();
        return new TireBrandComparisonDto(COMPARISON_MIN_KM, brands);
    }

    // ---------------------------------------------------------------- internos

    private interface DetailSink {
        void add(String field, String message);
    }

    private List<Reading> parseReadings(Vehicle vehicle, List<TreadReadingInput> inputs, DetailSink details) {
        Map<TirePosition, TireMounting> open = new HashMap<>();
        mountingRepository.findByVehicle_IdAndRemovedAtIsNull(vehicle.getId()).forEach(m -> open.put(m.getPosition(), m));
        List<Reading> readings = new ArrayList<>();
        Set<TirePosition> seen = EnumSet.noneOf(TirePosition.class);
        for (int i = 0; i < inputs.size(); i++) {
            TreadReadingInput input = inputs.get(i);
            String field = "treadReadings[" + i + "]";
            TirePosition position = input == null ? null : TirePosition.fromJson(input.position());
            TireMounting mounting = position == null ? null : open.get(position);
            if (mounting == null) {
                details.add(field + ".position", "No hay una cubierta montada en esa posición");
                continue;
            }
            if (!seen.add(position)) {
                details.add(field + ".position", "Posición repetida");
                continue;
            }
            BigDecimal depth = input.depthMm();
            if (depth == null || depth.signum() < 0 || depth.compareTo(MAX_TREAD_MM) > 0) {
                details.add(field + ".depthMm", "Entre 0 y " + MAX_TREAD_MM + " mm");
            } else if (depth.stripTrailingZeros().scale() > 1) {
                details.add(field + ".depthMm", "Hasta 1 decimal");
            } else {
                readings.add(new Reading(mounting, depth));
            }
        }
        return readings;
    }

    private void record(Vehicle vehicle, List<Reading> readings, Instant at, UUID by, String byName, UUID inspectionId) {
        for (Reading reading : readings) {
            TireMounting mounting = reading.mounting();
            Tire tire = mounting.getTire();
            readingRepository.save(new TireTreadReading(tire, vehicle.getId(), mounting.getPosition(), reading.depthMm(),
                    at, by, byName, inspectionId));
            if (reading.depthMm().compareTo(minTreadMm) < 0) {
                if (tire.getTreadAlertedAt() == null) {
                    tire.setTreadAlertedAt(at);
                    eventPublisher.publishEvent(new TireTreadLowEvent(vehicle.getId(), vehicle.getPlate(), tireLabel(tire),
                            labelOf(vehicle, mounting.getPosition()), reading.depthMm(), minTreadMm, byName));
                }
            } else {
                tire.setTreadAlertedAt(null);
            }
            tireRepository.save(tire);
        }
    }

    private void remove(TireMounting mounting, Instant at, boolean retire, String reason) {
        mounting.close(at, mounting.getVehicle().getOdometerKm());
        mountingRepository.save(mounting);
        Tire tire = mounting.getTire();
        if (retire) {
            tire.retire(at, reason(reason));
        } else {
            tire.toStock();
        }
        tireRepository.save(tire);
    }

    private Optional<TireMounting> openAt(Vehicle vehicle, TirePosition position) {
        return mountingRepository.findByVehicle_IdAndRemovedAtIsNull(vehicle.getId()).stream()
                .filter(m -> m.getPosition() == position)
                .findFirst();
    }

    private TirePosition requirePosition(Vehicle vehicle, String raw, String field) {
        if (vehicle.getTireLayout() == null) {
            throw new VehicleStateConflictException("NO_TIRE_LAYOUT",
                    "Primero elegí la configuración de ejes de " + vehicle.getPlate());
        }
        TirePosition position = TirePosition.fromJson(raw);
        if (position == null || !vehicle.getTireLayout().has(position)) {
            throw invalid("Posición inválida", field, "No es una posición de " + vehicle.getTireLayout().label());
        }
        return position;
    }

    private record ValidTire(String code, String brand, String model, String size, LocalDate purchaseDate,
                             BigDecimal cost, BigDecimal initialTreadMm) {
    }

    private ValidTire validate(TireRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request == null) {
            throw invalid("Datos inválidos para la cubierta", "brand", "Obligatoria");
        }
        String brand = text(request.brand(), "brand", 60, true, details);
        String size = text(request.size(), "size", 30, true, details);
        String code = text(request.code(), "code", 40, false, details);
        String model = text(request.model(), "model", 60, false, details);
        LocalDate purchaseDate = null;
        if (request.purchaseDate() == null || request.purchaseDate().isBlank()) {
            details.add(new FieldValidationErrorDetail("purchaseDate", "Obligatoria"));
        } else {
            try {
                purchaseDate = LocalDate.parse(request.purchaseDate().trim());
                if (purchaseDate.isAfter(LocalDate.now(clock.withZone(FLEET_ZONE)))) {
                    details.add(new FieldValidationErrorDetail("purchaseDate", "No puede ser una fecha futura"));
                }
            } catch (DateTimeParseException e) {
                details.add(new FieldValidationErrorDetail("purchaseDate", "Fecha inválida, formato esperado YYYY-MM-DD"));
            }
        }
        BigDecimal cost = request.cost();
        if (cost == null || cost.signum() <= 0) {
            details.add(new FieldValidationErrorDetail("cost", "Debe ser mayor a 0"));
        } else if (cost.compareTo(MAX_COST) > 0) {
            details.add(new FieldValidationErrorDetail("cost", "Es demasiado alto"));
        } else if (cost.stripTrailingZeros().scale() > 2) {
            details.add(new FieldValidationErrorDetail("cost", "Hasta 2 decimales"));
        }
        BigDecimal tread = request.initialTreadMm();
        if (tread != null && (tread.signum() <= 0 || tread.compareTo(MAX_TREAD_MM) > 0
                || tread.stripTrailingZeros().scale() > 1)) {
            details.add(new FieldValidationErrorDetail("initialTreadMm", "Entre 0 y " + MAX_TREAD_MM + " mm, con hasta 1 decimal"));
        }
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para la cubierta", details);
        }
        return new ValidTire(code, brand, model, size, purchaseDate, cost, tread);
    }

    private static String text(String raw, String field, int max, boolean required, List<FieldValidationErrorDetail> details) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            if (required) {
                details.add(new FieldValidationErrorDetail(field, "Obligatorio"));
            }
            return null;
        }
        if (value.length() > max) {
            details.add(new FieldValidationErrorDetail(field, "Hasta " + max + " caracteres"));
        }
        return value;
    }

    private static String reason(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static VehicleValidationException invalid(String message, String field, String detail) {
        return new VehicleValidationException(message, List.of(new FieldValidationErrorDetail(field, detail)));
    }

    private static VehicleStateConflictException duplicateCode(String code) {
        return new VehicleStateConflictException("DUPLICATE_TIRE_CODE", "Ya hay una cubierta con el código " + code);
    }

    private Tire find(String id) {
        return Uuids.parse(id).flatMap(tireRepository::findById).orElseThrow(() -> new TireNotFoundException(id));
    }

    private Vehicle findVehicle(String id) {
        return Uuids.parse(id).flatMap(vehicleRepository::findById).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private Vehicle findActiveVehicle(String id) {
        Vehicle vehicle = findVehicle(id);
        if (!vehicle.isActive()) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE", "El vehículo está dado de baja.");
        }
        return vehicle;
    }

    static String tireLabel(Tire tire) {
        StringBuilder label = new StringBuilder(tire.getBrand());
        if (tire.getModel() != null) {
            label.append(' ').append(tire.getModel());
        }
        if (tire.getCode() != null) {
            label.append(" N° ").append(tire.getCode());
        }
        return label.toString();
    }

    private static String labelOf(Vehicle vehicle, TirePosition position) {
        TireLayout layout = vehicle.getTireLayout() != null ? vehicle.getTireLayout() : TireLayout.DIEZ_RUEDAS;
        return layout.labelOf(position);
    }

    private VehicleTiresDto toVehicleDto(Vehicle vehicle) {
        Snapshot snapshot = snapshot();
        Map<TirePosition, TireMounting> open = new HashMap<>();
        mountingRepository.findByVehicle_IdAndRemovedAtIsNull(vehicle.getId()).forEach(m -> open.put(m.getPosition(), m));
        TireLayout layout = vehicle.getTireLayout();
        List<VehicleTiresDto.Position> positions = layout == null ? List.of() : layout.positions().stream()
                .map(p -> new VehicleTiresDto.Position(p.toJson(), layout.labelOf(p), p.axle(),
                        p.side() == null ? null : p.side().name().toLowerCase(), p.inner(),
                        open.containsKey(p) ? snapshot.toDto(open.get(p).getTire()) : null))
                .toList();
        List<TireMountingDto> history = mountingRepository.findByVehicle_IdOrderByMountedAtDesc(vehicle.getId()).stream()
                .map(this::toMountingDto)
                .toList();
        return new VehicleTiresDto(vehicle.getId().toString(), vehicle.getPlate(), layout == null ? null : layout.toJson(),
                vehicle.getOdometerKm(), minTreadMm, positions, history);
    }

    private TireMountingDto toMountingDto(TireMounting m) {
        return new TireMountingDto(m.getTire().getId().toString(), tireLabel(m.getTire()), m.getVehicle().getId().toString(),
                m.getVehicle().getPlate(), m.getPosition().toJson(), labelOf(m.getVehicle(), m.getPosition()),
                m.getMountedAt().toString(), m.getMountedKm(), m.getRemovedAt() == null ? null : m.getRemovedAt().toString(),
                m.getRemovedKm(), m.km());
    }

    private TireDto.Tread toTread(TireTreadReading r) {
        return new TireDto.Tread(r.getDepthMm(), r.getMeasuredAt().toString(), r.getMeasuredByName(),
                r.getDepthMm().compareTo(minTreadMm) < 0);
    }

    /** Montajes y última medición de todas las cubiertas, leídos una vez por pedido. */
    private Snapshot snapshot() {
        Map<UUID, List<TireMounting>> mountings = mountingRepository.findAllWithTireAndVehicle().stream()
                .collect(Collectors.groupingBy(m -> m.getTire().getId()));
        Map<UUID, TireTreadReading> lastReading = new HashMap<>();
        for (TireTreadReading reading : readingRepository.findAllByOrderByMeasuredAtDesc()) {
            lastReading.putIfAbsent(reading.getTire().getId(), reading);
        }
        return new Snapshot(mountings, lastReading);
    }

    private final class Snapshot {
        private final Map<UUID, List<TireMounting>> mountings;
        private final Map<UUID, TireTreadReading> lastReading;

        Snapshot(Map<UUID, List<TireMounting>> mountings, Map<UUID, TireTreadReading> lastReading) {
            this.mountings = mountings;
            this.lastReading = lastReading;
        }

        long km(Tire tire) {
            return tire.getId() == null ? 0 : mountings.getOrDefault(tire.getId(), List.of()).stream()
                    .mapToLong(TireMounting::km).sum();
        }

        TireDto toDto(Tire tire) {
            long km = km(tire);
            TireMounting open = tire.getId() == null ? null : mountings.getOrDefault(tire.getId(), List.of()).stream()
                    .filter(TireMounting::isOpen).findFirst().orElse(null);
            TireDto.Current current = open == null ? null : new TireDto.Current(open.getVehicle().getId().toString(),
                    open.getVehicle().getPlate(), open.getPosition().toJson(), labelOf(open.getVehicle(), open.getPosition()),
                    open.getMountedAt().toString(), open.getMountedKm());
            TireTreadReading reading = tire.getId() == null ? null : lastReading.get(tire.getId());
            return new TireDto(tire.getId() == null ? null : tire.getId().toString(), tire.getCode(), tire.getBrand(),
                    tire.getModel(), tire.getSize(), tire.getPurchaseDate().toString(), tire.getCost(),
                    tire.getInitialTreadMm(), tire.getStatus().toJson(),
                    tire.getRetiredAt() == null ? null : tire.getRetiredAt().toString(), tire.getRetiredReason(), km,
                    km == 0 ? null : tire.getCost().divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP), current,
                    reading == null ? null : toTread(reading));
        }
    }
}
