package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.FuelLoadDto;
import org.fleetguard.dto.FuelLoadRequest;
import org.fleetguard.dto.UserRefDto;
import org.fleetguard.dto.VehicleFuelDto;
import org.fleetguard.entity.FuelLoad;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.event.FuelConsumptionAnomalyEvent;
import org.fleetguard.exception.FuelLoadNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.FuelLoadRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.service.FuelConsumption.Load;
import org.fleetguard.service.FuelConsumption.Segment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CAM-156: cargas de combustible, consumo por vehículo y aviso de consumo anormal. Ver
 * docs/api/CAM-156-fuel-contract.md.
 */
@Service
public class FuelLoadService {

    static final ZoneId FLEET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    // Lo que entra en las columnas: numeric(10, 2) y numeric(12, 2).
    static final BigDecimal MAX_LITERS = new BigDecimal("2000");
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    static final int MAX_MONTHS = 24;
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(FLEET_ZONE);

    private final FuelLoadRepository fuelLoadRepository;
    private final VehicleRepository vehicleRepository;
    private final OdometerReadings odometerReadings;
    private final UserNameService userNameService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Rules rules;

    public FuelLoadService(FuelLoadRepository fuelLoadRepository, VehicleRepository vehicleRepository,
                           OdometerReadings odometerReadings, UserNameService userNameService,
                           ApplicationEventPublisher eventPublisher, Clock clock,
                           @Value("${fleetguard.fuel-alerts.min-segments:3}") int minSegments,
                           @Value("${fleetguard.fuel-alerts.recent-segments:10}") int recentSegments,
                           @Value("${fleetguard.fuel-alerts.ratio:1.3}") double ratio) {
        this.fuelLoadRepository = fuelLoadRepository;
        this.vehicleRepository = vehicleRepository;
        this.odometerReadings = odometerReadings;
        this.userNameService = userNameService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.rules = new Rules(minSegments, recentSegments, BigDecimal.valueOf(ratio));
    }

    /**
     * Las reglas del aviso, configurables en application.yml (fleetguard.fuel-alerts).
     *
     * @param minSegments    tramos anteriores que hacen falta para tener un promedio
     * @param recentSegments cuántos de los últimos tramos entran en el promedio
     * @param ratio          un tramo es anormal si consume más de ratio veces el promedio
     */
    record Rules(int minSegments, int recentSegments, BigDecimal ratio) {
    }

    @Transactional
    public FuelLoadDto create(FuelLoadRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw invalid(List.of(new FieldValidationErrorDetail("vehicleId", "Obligatorio")));
        }
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.vehicleId() == null || request.vehicleId().isBlank()) {
            details.add(new FieldValidationErrorDetail("vehicleId", "Obligatorio"));
        }
        Valid valid = validate(request, details);
        Vehicle vehicle = Uuids.parse(request.vehicleId().trim()).flatMap(vehicleRepository::findById)
                .orElseThrow(() -> new VehicleNotFoundException(request.vehicleId()));
        if (!vehicle.isActive()) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE", "El vehículo está dado de baja.");
        }
        if (vehicle.getVehicleType() != null && vehicle.getVehicleType().isTrailer()) {
            throw new VehicleStateConflictException("VEHICLE_IS_TRAILER", vehicle.getPlate() + " es un remolque: no carga combustible.");
        }
        List<FuelLoad> history = fuelLoadRepository.findByVehicle_IdOrderByLoadedAtAscOdometerKmAsc(vehicle.getId());
        requireKmInOrder(history, null, valid.loadedAt(), valid.odometerKm());

        FuelLoad load = fuelLoadRepository.save(new FuelLoad(vehicle, valid.loadedAt(), valid.liters(), valid.amount(),
                valid.odometerKm(), valid.fullTank(), user == null ? null : user.id()));
        odometerReadings.apply(vehicle, valid.odometerKm());

        List<FuelLoad> withNew = new ArrayList<>(history);
        withNew.add(load);
        withNew.sort(ORDER);
        List<Segment> segments = FuelConsumption.segments(toLoads(withNew));
        checkAnomaly(vehicle, load, segments);
        return toDto(load, segments, names(List.of(load)));
    }

    /** Solo el admin. No mueve el odómetro del vehículo ni vuelve a avisar. */
    @Transactional
    public FuelLoadDto update(String id, FuelLoadRequest request) {
        FuelLoad load = find(id);
        if (request == null) {
            throw invalid(List.of(new FieldValidationErrorDetail("liters", "Obligatorio")));
        }
        Valid valid = validate(request, new ArrayList<>());
        List<FuelLoad> history = fuelLoadRepository.findByVehicle_IdOrderByLoadedAtAscOdometerKmAsc(load.getVehicle().getId());
        requireKmInOrder(history, load.getId(), valid.loadedAt(), valid.odometerKm());
        load.update(load.getVehicle(), valid.loadedAt(), valid.liters(), valid.amount(), valid.odometerKm(), valid.fullTank());
        FuelLoad saved = fuelLoadRepository.save(load);
        List<FuelLoad> all = new ArrayList<>(history);
        all.sort(ORDER);
        return toDto(saved, FuelConsumption.segments(toLoads(all)), names(List.of(saved)));
    }

    @Transactional
    public void delete(String id) {
        fuelLoadRepository.delete(find(id));
    }

    @Transactional(readOnly = true)
    public VehicleFuelDto vehicleFuel(String vehicleIdRaw, Integer monthsParam) {
        int months = monthsParam == null ? 6 : monthsParam;
        if (months < 1 || months > MAX_MONTHS) {
            throw invalid(List.of(new FieldValidationErrorDetail("months", "Entre 1 y " + MAX_MONTHS)));
        }
        Vehicle vehicle = Uuids.parse(vehicleIdRaw).flatMap(vehicleRepository::findById)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleIdRaw));
        List<FuelLoad> loads = fuelLoadRepository.findByVehicle_IdOrderByLoadedAtAscOdometerKmAsc(vehicle.getId());
        List<Segment> segments = FuelConsumption.segments(toLoads(loads));

        YearMonth current = YearMonth.now(clock.withZone(FLEET_ZONE));
        YearMonth first = current.minusMonths(months - 1L);
        Instant from = first.atDay(1).atStartOfDay(FLEET_ZONE).toInstant();

        List<FuelLoad> inWindow = loads.stream().filter(l -> !l.getLoadedAt().isBefore(from)).toList();
        List<Segment> segmentsInWindow = segments.stream().filter(s -> !s.closedAt().isBefore(from)).toList();

        List<VehicleFuelDto.Month> monthly = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            YearMonth m = month;
            List<FuelLoad> monthLoads = inWindow.stream().filter(l -> monthOf(l.getLoadedAt()).equals(m)).toList();
            List<Segment> monthSegments = segmentsInWindow.stream().filter(s -> monthOf(s.closedAt()).equals(m)).toList();
            monthly.add(new VehicleFuelDto.Month(m.toString(), sumLiters(monthLoads), sumAmount(monthLoads),
                    monthSegments.stream().mapToLong(Segment::km).sum(), FuelConsumption.litersPer100Km(monthSegments),
                    FuelConsumption.costPerKm(monthSegments)));
        }

        VehicleFuelDto.Summary summary = new VehicleFuelDto.Summary(inWindow.size(), sumLiters(inWindow),
                sumAmount(inWindow), segmentsInWindow.stream().mapToLong(Segment::km).sum(),
                FuelConsumption.litersPer100Km(segmentsInWindow), FuelConsumption.costPerKm(segmentsInWindow));
        Map<String, String> names = names(inWindow);
        List<FuelLoadDto> dtos = inWindow.stream()
                .sorted(ORDER.reversed())
                .map(l -> toDto(l, segments, names))
                .toList();
        return new VehicleFuelDto(vehicle.getId().toString(), vehicle.getPlate(), months, summary, monthly, dtos);
    }

    /** Para Gastos (CAM-156): las cargas del período [from, to). */
    @Transactional(readOnly = true)
    public List<FuelLoad> loadsInRange(Instant from, Instant to) {
        return fuelLoadRepository.findInRangeWithVehicle(from, to);
    }

    /** Descripción de la carga en Gastos: "Carga de 85,5 L a los 152.300 km". */
    public static String expenseDescription(FuelLoad load) {
        NumberFormat liters = NumberFormat.getNumberInstance(AR);
        liters.setMaximumFractionDigits(2);
        return "Carga de " + liters.format(load.getLiters()) + " L a los "
                + NumberFormat.getIntegerInstance(AR).format(load.getOdometerKm()) + " km"
                + (load.isFullTank() ? "" : " (parcial)");
    }

    private static final Comparator<FuelLoad> ORDER = Comparator.comparing(FuelLoad::getLoadedAt)
            .thenComparingLong(FuelLoad::getOdometerKm);

    private void checkAnomaly(Vehicle vehicle, FuelLoad load, List<Segment> segments) {
        int index = -1;
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).closingLoadId().equals(load.getId())) {
                index = i;
            }
        }
        if (index < rules.minSegments()) {
            return;
        }
        Segment segment = segments.get(index);
        List<Segment> previous = segments.subList(Math.max(0, index - rules.recentSegments()), index);
        BigDecimal average = FuelConsumption.litersPer100Km(previous);
        BigDecimal consumption = segment.litersPer100Km();
        if (average == null || consumption == null || average.signum() == 0) {
            return;
        }
        if (consumption.compareTo(average.multiply(rules.ratio())) <= 0) {
            return;
        }
        load.markAlert(consumption, average);
        eventPublisher.publishEvent(new FuelConsumptionAnomalyEvent(vehicle.getId(), vehicle.getPlate(), consumption,
                average, segment.km()));
    }

    /** La carga anterior (por fecha) no puede tener más km, ni la siguiente menos. */
    private static void requireKmInOrder(List<FuelLoad> history, UUID self, Instant loadedAt, long km) {
        FuelLoad before = null;
        FuelLoad after = null;
        for (FuelLoad other : history) {
            if (other.getId() != null && other.getId().equals(self)) {
                continue;
            }
            if (!other.getLoadedAt().isAfter(loadedAt)) {
                before = other;
            } else if (after == null) {
                after = other;
            }
        }
        if (before != null && km < before.getOdometerKm()) {
            throw invalid(List.of(new FieldValidationErrorDetail("odometerKm",
                    "No puede ser menor a los " + km(before.getOdometerKm()) + " de la carga anterior ("
                            + DAY.format(before.getLoadedAt()) + ")")));
        }
        if (after != null && km > after.getOdometerKm()) {
            throw invalid(List.of(new FieldValidationErrorDetail("odometerKm",
                    "No puede ser mayor a los " + km(after.getOdometerKm()) + " de la carga siguiente ("
                            + DAY.format(after.getLoadedAt()) + ")")));
        }
    }

    private record Valid(Instant loadedAt, BigDecimal liters, BigDecimal amount, long odometerKm, boolean fullTank) {
    }

    private Valid validate(FuelLoadRequest request, List<FieldValidationErrorDetail> details) {
        Instant now = clock.instant();
        Instant loadedAt = now;
        if (request.loadedAt() != null && !request.loadedAt().isBlank()) {
            try {
                loadedAt = Instant.parse(request.loadedAt().trim());
                if (loadedAt.isAfter(now.plus(CLOCK_SKEW))) {
                    details.add(new FieldValidationErrorDetail("loadedAt", "No puede ser una fecha futura"));
                } else if (loadedAt.isAfter(now)) {
                    loadedAt = now;
                }
            } catch (DateTimeParseException e) {
                details.add(new FieldValidationErrorDetail("loadedAt", "Fecha inválida, formato esperado ISO-8601"));
            }
        }
        validatePositive(request.liters(), "liters", MAX_LITERS, "Hasta " + MAX_LITERS + " litros por carga", details);
        validatePositive(request.amount(), "amount", MAX_AMOUNT, "Es demasiado alto", details);
        if (request.odometerKm() == null) {
            details.add(new FieldValidationErrorDetail("odometerKm", "Obligatorio"));
        } else if (request.odometerKm() < 0) {
            details.add(new FieldValidationErrorDetail("odometerKm", "No puede ser negativo"));
        }
        if (!details.isEmpty()) {
            throw invalid(details);
        }
        return new Valid(loadedAt, request.liters(), request.amount(), request.odometerKm(),
                request.fullTank() == null || request.fullTank());
    }

    private static void validatePositive(BigDecimal value, String field, BigDecimal max, String tooHigh,
                                         List<FieldValidationErrorDetail> details) {
        if (value == null || value.signum() <= 0) {
            details.add(new FieldValidationErrorDetail(field, "Debe ser mayor a 0"));
        } else if (value.compareTo(max) > 0) {
            details.add(new FieldValidationErrorDetail(field, tooHigh));
        } else if (value.stripTrailingZeros().scale() > 2) {
            details.add(new FieldValidationErrorDetail(field, "Hasta 2 decimales"));
        }
    }

    private static VehicleValidationException invalid(List<FieldValidationErrorDetail> details) {
        return new VehicleValidationException("Datos inválidos para la carga de combustible", details);
    }

    private FuelLoad find(String id) {
        return Uuids.parse(id).flatMap(fuelLoadRepository::findById).orElseThrow(() -> new FuelLoadNotFoundException(id));
    }

    private static List<Load> toLoads(List<FuelLoad> loads) {
        return loads.stream()
                .map(l -> new Load(l.getId(), l.getLoadedAt(), l.getOdometerKm(), l.getLiters(), l.getAmount(), l.isFullTank()))
                .toList();
    }

    private Map<String, String> names(List<FuelLoad> loads) {
        return userNameService.currentNames(loads.stream().map(FuelLoad::getCreatedBy).filter(Objects::nonNull)
                .map(UUID::toString).distinct().toList());
    }

    private static FuelLoadDto toDto(FuelLoad load, List<Segment> segments, Map<String, String> names) {
        Map<UUID, Segment> byClosingLoad = segments.stream()
                .collect(Collectors.toMap(Segment::closingLoadId, Function.identity(), (a, b) -> a));
        Segment segment = load.getId() == null ? null : byClosingLoad.get(load.getId());
        FuelLoadDto.Consumption consumption = segment == null ? null : new FuelLoadDto.Consumption(segment.km(),
                segment.liters(), segment.litersPer100Km(), segment.costPerKm());
        FuelLoadDto.Alert alert = load.getAlertLitersPer100Km() == null ? null
                : new FuelLoadDto.Alert(load.getAlertLitersPer100Km(), load.getAlertAverageLitersPer100Km());
        String createdBy = load.getCreatedBy() == null ? null : load.getCreatedBy().toString();
        UserRefDto user = createdBy == null ? null : new UserRefDto(createdBy, names.get(createdBy));
        return new FuelLoadDto(load.getId() == null ? null : load.getId().toString(), load.getVehicle().getId().toString(),
                load.getVehicle().getPlate(), load.getLoadedAt().toString(), load.getLiters(), load.getAmount(),
                load.getOdometerKm(), load.isFullTank(), user, consumption, alert);
    }

    private static YearMonth monthOf(Instant instant) {
        return YearMonth.from(instant.atZone(FLEET_ZONE));
    }

    private static BigDecimal sumLiters(List<FuelLoad> loads) {
        return loads.stream().map(FuelLoad::getLiters).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumAmount(List<FuelLoad> loads) {
        return loads.stream().map(FuelLoad::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String km(long value) {
        return NumberFormat.getIntegerInstance(AR).format(value) + " km";
    }
}
