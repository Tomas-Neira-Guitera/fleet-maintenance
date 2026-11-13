package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.MountTireRequest;
import org.fleetguard.dto.RetireTireRequest;
import org.fleetguard.dto.RotateTiresRequest;
import org.fleetguard.dto.TireBrandComparisonDto;
import org.fleetguard.dto.TireDetailDto;
import org.fleetguard.dto.TireDto;
import org.fleetguard.dto.TireMountingDto;
import org.fleetguard.dto.TireConditionInput;
import org.fleetguard.dto.TireConditionReportDto;
import org.fleetguard.dto.TirePositionDto;
import org.fleetguard.dto.TirePositionsDto;
import org.fleetguard.dto.TireRequest;
import org.fleetguard.dto.UnmountTireRequest;
import org.fleetguard.dto.ValidationErrorDetail;
import org.fleetguard.dto.VehicleTiresDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Tire;
import org.fleetguard.entity.TireCondition;
import org.fleetguard.entity.TireConditionReport;
import org.fleetguard.entity.TireMounting;
import org.fleetguard.entity.TirePosition;
import org.fleetguard.entity.TireStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleType;
import org.fleetguard.event.TireProblemReportedEvent;
import org.fleetguard.exception.InspectionValidationException;
import org.fleetguard.exception.TireNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.TireMountingRepository;
import org.fleetguard.repository.TireRepository;
import org.fleetguard.repository.TireConditionReportRepository;
import org.fleetguard.repository.VehicleRepository;
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
 * CAM-157: cubiertas, dónde está montada cada una, sus km y los problemas que reportan los choferes. Ver docs/api/CAM-157-tires-contract.md.
 * Los km de una cubierta salen de sus montajes y del odómetro de cada vehículo: rotarla cierra un montaje y
 * abre otro, así que sigue sumando sin lógica aparte.
 */
@Service
public class TireService {

    private static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    static final BigDecimal MAX_COST = new BigDecimal("9999999999.99");
    // Para comparar marcas: una cubierta con pocos km todavía da un costo por km engañoso.
    static final long COMPARISON_MIN_KM = 1000;

    private final TireRepository tireRepository;
    private final TireMountingRepository mountingRepository;
    private final TireConditionReportRepository reportRepository;
    private final VehicleRepository vehicleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TireService(TireRepository tireRepository, TireMountingRepository mountingRepository,
                       TireConditionReportRepository reportRepository, VehicleRepository vehicleRepository,
                       ApplicationEventPublisher eventPublisher, Clock clock) {
        this.tireRepository = tireRepository;
        this.mountingRepository = mountingRepository;
        this.reportRepository = reportRepository;
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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
        List<TireConditionReportDto> reports = reportRepository.findByTireWithDefect(tire.getId()).stream()
                .map(TireService::toReportDto)
                .toList();
        return new TireDetailDto(snapshot.toDto(tire), mountings, reports);
    }

    @Transactional
    public TireDto create(TireRequest request) {
        ValidTire valid = validate(request);
        if (valid.code() != null && tireRepository.existsByCodeIgnoreCase(valid.code())) {
            throw duplicateCode(valid.code());
        }
        Tire tire = tireRepository.save(new Tire(valid.code(), valid.brand(), valid.model(), valid.size(),
                valid.purchaseDate(), valid.cost()));
        return snapshot().toDto(tire);
    }

    @Transactional
    public TireDto update(String id, TireRequest request) {
        Tire tire = find(id);
        ValidTire valid = validate(request);
        if (valid.code() != null && tireRepository.existsByCodeIgnoreCaseAndIdNot(valid.code(), tire.getId())) {
            throw duplicateCode(valid.code());
        }
        tire.update(valid.code(), valid.brand(), valid.model(), valid.size(), valid.purchaseDate(), valid.cost());
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

    /** Para el pre-viaje del chofer: el dibujo del vehículo; solo se pueden marcar las posiciones con cubierta. */
    @Transactional(readOnly = true)
    public TirePositionsDto positions(String vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        VehicleType type = vehicle.getVehicleType();
        if (type == null || type.isTrailer()) {
            return new TirePositionsDto(VehicleType.toJson(type), List.of());
        }
        Set<TirePosition> mounted = openByPosition(vehicle).keySet();
        return new TirePositionsDto(type.toJson(), type.positions().stream()
                .map(p -> new TirePositionDto(p.toJson(), type.labelOf(p), p.axle(),
                        p.side() == null ? null : p.side().name().toLowerCase(), p.wheel().name().toLowerCase(),
                        mounted.contains(p)))
                .toList());
    }

    // ---------------------------------------------------------------- estado visual

    /** Lo que el chofer vio en una cubierta, ya validado: la que está montada en esa posición. */
    public record ConditionReport(TireMounting mounting, Set<TireCondition> conditions) {
    }

    /**
     * CAM-157: valida las cubiertas con problemas antes de guardar la inspección. Van solo con "Neumáticos" en
     * defecto; sin cubiertas marcadas también vale (por ejemplo, un vehículo sin tipo).
     */
    public List<ConditionReport> validateInspectionConditions(Vehicle vehicle, List<TireConditionInput> inputs,
                                                              boolean tiresInDefect) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        if (!tiresInDefect) {
            throw new InspectionValidationException("Las cubiertas con problemas van con Neumáticos en defecto.",
                    List.of(new ValidationErrorDetail("tireConditions", "Marcá Neumáticos con defecto.")));
        }
        Map<TirePosition, TireMounting> open = openByPosition(vehicle);
        Set<TirePosition> seen = EnumSet.noneOf(TirePosition.class);
        List<ValidationErrorDetail> details = new ArrayList<>();
        List<ConditionReport> reports = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            TireConditionInput input = inputs.get(i);
            String field = "tireConditions[" + i + "]";
            TirePosition position = input == null ? null : TirePosition.fromJson(input.position());
            TireMounting mounting = position == null ? null : open.get(position);
            if (mounting == null) {
                details.add(new ValidationErrorDetail(field + ".position", "No hay una cubierta montada en esa posición."));
                continue;
            }
            if (!seen.add(position)) {
                details.add(new ValidationErrorDetail(field + ".position", "Posición repetida."));
                continue;
            }
            List<String> raw = input.conditions() == null ? List.of() : input.conditions();
            Set<TireCondition> conditions = EnumSet.noneOf(TireCondition.class);
            for (String code : raw) {
                TireCondition condition = TireCondition.fromJson(code);
                if (condition == null) {
                    details.add(new ValidationErrorDetail(field + ".conditions", "Estado desconocido: " + code));
                } else {
                    conditions.add(condition);
                }
            }
            if (raw.isEmpty()) {
                details.add(new ValidationErrorDetail(field + ".conditions", "Elegí al menos un estado."));
            } else if (!conditions.isEmpty()) {
                reports.add(new ConditionReport(mounting, conditions));
            }
        }
        if (!details.isEmpty()) {
            throw new InspectionValidationException("Las cubiertas con problemas no son válidas.", details);
        }
        return reports;
    }

    /** Lo que se suma al detalle del defecto de Neumáticos: "Cubiertas: Delantera izquierda: pinchada." */
    public String summary(Vehicle vehicle, List<ConditionReport> reports) {
        return reports.stream()
                .map(r -> labelOf(vehicle, r.mounting().getPosition()) + ": " + conditionsText(r.conditions()))
                .collect(Collectors.joining(". ", "Cubiertas: ", "."));
    }

    @Transactional
    public void recordInspectionConditions(Vehicle vehicle, List<ConditionReport> reports, Instant at,
                                           AuthenticatedUser driver, UUID inspectionId, Defect defect) {
        if (reports.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (ConditionReport report : reports) {
            TireMounting mounting = report.mounting();
            reportRepository.save(new TireConditionReport(mounting.getTire(), vehicle, mounting.getPosition(),
                    report.conditions(), at, driver == null ? null : driver.id(),
                    driver == null ? null : driver.username(), inspectionId, defect));
            lines.add(labelOf(vehicle, mounting.getPosition()) + " (" + tireLabel(mounting.getTire()) + "): "
                    + conditionsText(report.conditions()));
        }
        eventPublisher.publishEvent(new TireProblemReportedEvent(vehicle.getId(), vehicle.getPlate(),
                driver == null ? null : driver.username(), lines));
    }

    private static String conditionsText(Set<TireCondition> conditions) {
        return conditions.stream()
                .map(c -> Character.toLowerCase(c.label().charAt(0)) + c.label().substring(1))
                .collect(Collectors.joining(", "));
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

    private Map<TirePosition, TireMounting> openByPosition(Vehicle vehicle) {
        Map<TirePosition, TireMounting> open = new HashMap<>();
        mountingRepository.findByVehicle_IdAndRemovedAtIsNull(vehicle.getId()).forEach(m -> open.put(m.getPosition(), m));
        return open;
    }

    private Optional<TireMounting> openAt(Vehicle vehicle, TirePosition position) {
        return mountingRepository.findByVehicle_IdAndRemovedAtIsNull(vehicle.getId()).stream()
                .filter(m -> m.getPosition() == position)
                .findFirst();
    }

    private TirePosition requirePosition(Vehicle vehicle, String raw, String field) {
        if (vehicle.getVehicleType() == null) {
            throw new VehicleStateConflictException("NO_VEHICLE_TYPE",
                    "Primero elegí el tipo de " + vehicle.getPlate() + ": de ahí salen sus cubiertas");
        }
        TirePosition position = TirePosition.fromJson(raw);
        if (position == null || !vehicle.getVehicleType().has(position)) {
            throw invalid("Posición inválida", field, "No es una posición de un " + vehicle.getVehicleType().label());
        }
        return position;
    }

    private record ValidTire(String code, String brand, String model, String size, LocalDate purchaseDate,
                             BigDecimal cost) {
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
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para la cubierta", details);
        }
        return new ValidTire(code, brand, model, size, purchaseDate, cost);
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
        VehicleType type = vehicle.getVehicleType() != null ? vehicle.getVehicleType() : VehicleType.CAMION_6X4;
        return type.labelOf(position);
    }

    private VehicleTiresDto toVehicleDto(Vehicle vehicle) {
        Snapshot snapshot = snapshot();
        Map<TirePosition, TireMounting> open = openByPosition(vehicle);
        VehicleType type = vehicle.getVehicleType();
        List<VehicleTiresDto.Position> positions = type == null ? List.of() : type.positions().stream()
                .map(p -> new VehicleTiresDto.Position(p.toJson(), type.labelOf(p), p.axle(),
                        p.side() == null ? null : p.side().name().toLowerCase(), p.wheel().name().toLowerCase(),
                        open.containsKey(p) ? snapshot.toDto(open.get(p).getTire()) : null))
                .toList();
        List<TireMountingDto> history = mountingRepository.findByVehicle_IdOrderByMountedAtDesc(vehicle.getId()).stream()
                .map(this::toMountingDto)
                .toList();
        return new VehicleTiresDto(vehicle.getId().toString(), vehicle.getPlate(), VehicleType.toJson(type),
                vehicle.getOdometerKm(), positions, history);
    }

    private TireMountingDto toMountingDto(TireMounting m) {
        return new TireMountingDto(m.getTire().getId().toString(), tireLabel(m.getTire()), m.getVehicle().getId().toString(),
                m.getVehicle().getPlate(), m.getPosition().toJson(), labelOf(m.getVehicle(), m.getPosition()),
                m.getMountedAt().toString(), m.getMountedKm(), m.getRemovedAt() == null ? null : m.getRemovedAt().toString(),
                m.getRemovedKm(), m.km());
    }

    private static TireConditionReportDto toReportDto(TireConditionReport r) {
        Vehicle vehicle = r.getVehicle();
        Defect defect = r.getDefect();
        return new TireConditionReportDto(vehicle.getId().toString(), vehicle.getPlate(), r.getPosition().toJson(),
                labelOf(vehicle, r.getPosition()), r.getConditions().stream().map(TireCondition::toJson).toList(),
                r.getConditions().stream().map(TireCondition::label).toList(),
                defect == null ? null : defect.getSeverity() == DefectSeverity.BLOCKING ? "blocking" : "non-blocking",
                r.getReportedAt().toString(), r.getReportedByName(), defect == null ? null : defect.getId().toString(),
                r.isActive());
    }

    /** Montajes y último problema abierto de todas las cubiertas, leídos una vez por pedido. */
    private Snapshot snapshot() {
        Map<UUID, List<TireMounting>> mountings = mountingRepository.findAllWithTireAndVehicle().stream()
                .collect(Collectors.groupingBy(m -> m.getTire().getId()));
        Map<UUID, TireConditionReport> openReport = new HashMap<>();
        for (TireConditionReport report : reportRepository.findAllWithTireAndDefect()) {
            if (report.isActive()) {
                openReport.putIfAbsent(report.getTire().getId(), report);
            }
        }
        return new Snapshot(mountings, openReport);
    }

    private static final class Snapshot {
        private final Map<UUID, List<TireMounting>> mountings;
        private final Map<UUID, TireConditionReport> openReport;

        Snapshot(Map<UUID, List<TireMounting>> mountings, Map<UUID, TireConditionReport> openReport) {
            this.mountings = mountings;
            this.openReport = openReport;
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
            TireConditionReport report = tire.getId() == null ? null : openReport.get(tire.getId());
            return new TireDto(tire.getId() == null ? null : tire.getId().toString(), tire.getCode(), tire.getBrand(),
                    tire.getModel(), tire.getSize(), tire.getPurchaseDate().toString(), tire.getCost(),
                    tire.getStatus().toJson(),
                    tire.getRetiredAt() == null ? null : tire.getRetiredAt().toString(), tire.getRetiredReason(), km,
                    km == 0 ? null : tire.getCost().divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP), current,
                    report == null ? null : toReportDto(report));
        }
    }
}
