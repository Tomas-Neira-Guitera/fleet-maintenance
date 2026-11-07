package org.fleetguard.service;

import org.fleetguard.dto.DowntimeDto;
import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.RecurringDefectsDto;
import org.fleetguard.dto.VehicleComparisonDto;
import org.fleetguard.dto.VehicleComparisonDto.Averages;
import org.fleetguard.dto.VehicleComparisonDto.Review;
import org.fleetguard.dto.VehicleComparisonDto.Row;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.service.KmPerDayEstimator.Reading;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GET /api/vehicles/comparison (CAM-140): los indicadores que ya existen de a un vehículo, juntos para
 * toda la flota en un solo pedido. Cada número sale del mismo lugar que lo muestra el resto de la app,
 * así coinciden: los gastos, del listado de Gastos (CAM-52, CAM-109); la disponibilidad, de
 * DowntimeService (CAM-134); las fallas recurrentes, de RecurringDefectService (CAM-135); y los km, de
 * las mismas lecturas que la fecha estimada (CAM-137). Ver CAM-140-vehicle-comparison-contract.md.
 */
@Service
public class VehicleComparisonService {

    static final String REASON_COST_PER_KM = "maintenance_cost_per_km";
    static final String REASON_AVAILABILITY = "availability";
    static final String REASON_RECURRING = "recurring_defects";
    /** Peor que el promedio en al menos esta cantidad de indicadores: "Para revisar". */
    static final int MIN_REASONS = 2;

    /**
     * Los km recorridos en el período y desde cuándo cuentan: el comienzo del período, o la primera
     * lectura si las lecturas arrancan más tarde. El costo por km divide solo los gastos desde ahí.
     */
    record KmSpan(long km, Instant from) {
    }

    private final VehicleRepository vehicleRepository;
    private final ExpenseService expenseService;
    private final DowntimeService downtimeService;
    private final RecurringDefectService recurringDefectService;
    private final KmPerDayEstimator kmReadings;
    private final Clock clock;
    private final long maxJumpKm;

    @Autowired
    public VehicleComparisonService(VehicleRepository vehicleRepository, ExpenseService expenseService,
                                    DowntimeService downtimeService, RecurringDefectService recurringDefectService,
                                    KmPerDayEstimator kmReadings, Clock clock,
                                    @Value("${fleetguard.odometer.max-jump-km:5000}") long maxJumpKm) {
        this.vehicleRepository = vehicleRepository;
        this.expenseService = expenseService;
        this.downtimeService = downtimeService;
        this.recurringDefectService = recurringDefectService;
        this.kmReadings = kmReadings;
        this.clock = clock;
        this.maxJumpKm = maxJumpKm;
    }

    /** Con el salto máximo por defecto, para tests. */
    public VehicleComparisonService(VehicleRepository vehicleRepository, ExpenseService expenseService,
                                    DowntimeService downtimeService, RecurringDefectService recurringDefectService,
                                    KmPerDayEstimator kmReadings, Clock clock) {
        this(vehicleRepository, expenseService, downtimeService, recurringDefectService, kmReadings, clock,
                KmPerDayEstimator.DEFAULT_MAX_JUMP_KM);
    }

    @Transactional(readOnly = true)
    public VehicleComparisonDto compare(String from, String to) {
        // Primero la disponibilidad: valida el período (from y to obligatorios, to posterior a from) y
        // devuelve el período efectivo, recortado a hoy.
        DowntimeDto downtime = downtimeService.get(from, to, null);
        Instant start = downtime.from();
        Instant end = downtime.to();

        List<Vehicle> vehicles = vehicleRepository.findByActive(true);
        Map<String, DowntimeDto.Vehicle> downtimeById = downtime.vehicles().stream()
                .collect(Collectors.toMap(DowntimeDto.Vehicle::vehicleId, Function.identity()));
        Map<String, List<ExpenseDto>> expensesById = expenseService.list(null, null, null, from, to).stream()
                .collect(Collectors.groupingBy(ExpenseDto::vehicleId));
        Map<String, Long> recurringById = recurringDefectService.list(null).items().stream()
                .collect(Collectors.groupingBy(RecurringDefectsDto.Item::vehicleId, Collectors.counting()));
        Map<UUID, List<Reading>> readings = kmReadings.readings(vehicles);
        Instant now = clock.instant();

        List<Row> rows = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            String id = vehicle.getId().toString();
            List<ExpenseDto> expenses = expensesById.getOrDefault(id, List.of());
            BigDecimal operating = sum(expenses.stream().filter(e -> "operating".equals(e.origin())).toList());
            BigDecimal maintenance = sum(expenses.stream().filter(e -> !"operating".equals(e.origin())).toList());
            BigDecimal total = maintenance.add(operating);
            KmSpan span = kmDriven(KmPerDayEstimator.validReadings(readings.getOrDefault(vehicle.getId(), List.of()),
                    vehicle.getOdometerKm(), now), start, end, maxJumpKm);
            Long km = span == null ? null : span.km();
            // Los gastos del tramo con km: si las lecturas arrancan a mitad del período, un gasto de
            // antes dividido por los km de después inflaría el costo por km y marcaría el vehículo.
            List<ExpenseDto> withKm = span == null ? List.of() : expenses.stream()
                    .filter(e -> !Instant.parse(e.date()).isBefore(span.from()))
                    .toList();
            BigDecimal maintenanceWithKm = sum(withKm.stream().filter(e -> !"operating".equals(e.origin())).toList());
            BigDecimal totalWithKm = sum(withKm);
            DowntimeDto.Vehicle down = downtimeById.get(id);
            rows.add(new Row(id, vehicle.getPlate(), vehicle.getBrand(), vehicle.getModel(), vehicle.getVehicleType(),
                    vehicle.getYear(), maintenance, operating, total, km, perKm(maintenanceWithKm, km),
                    perKm(totalWithKm, km),
                    down == null ? null : down.availabilityPct(), down == null ? 0 : down.downtimeHours(),
                    down == null ? 0 : down.stops(), recurringById.getOrDefault(id, 0L).intValue(), null));
        }

        // La marca se decide contra el promedio sin redondear: con 1,00 y 1,01 el promedio es 1,005, y
        // redondeado a 1,01 dejaría de marcar al que está peor. A la respuesta va redondeado.
        Averages exact = averages(rows, false);
        List<Row> reviewed = rows.stream()
                .map(row -> withReview(row, review(row, exact)))
                .sorted(Comparator.comparing(Row::plate, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new VehicleComparisonDto(start, end, reviewed, averages(rows, true));
    }

    /**
     * Los km del período: del km al empezar el período al km al terminarlo.
     *
     * <ul>
     *   <li>Se parte de las lecturas válidas de CAM-137 y, como ahí, se descartan las primeras de la
     *       historia que tienen más de {@code maxJumpKm} hasta la siguiente: un vehículo dado de alta con
     *       309.000 km cuya primera inspección se cargó con 310 no anduvo 309.000 km en el período.</li>
     *   <li>El km de inicio es la última lectura hasta el comienzo. Si no hay, la primera dentro del
     *       período, y los km cuentan desde esa fecha (KmSpan.from).</li>
     *   <li>El de fin es la última lectura hasta el final.</li>
     *   <li>Null si falta alguno de los dos o son la misma lectura: sin dos lecturas no se sabe cuánto
     *       anduvo, y 0 diría que no se movió.</li>
     * </ul>
     */
    static KmSpan kmDriven(List<Reading> validReadings, Instant start, Instant end, long maxJumpKm) {
        List<Reading> trusted = new ArrayList<>(validReadings);
        while (trusted.size() >= 2 && trusted.get(1).km() - trusted.get(0).km() > maxJumpKm) {
            trusted.remove(0);
        }
        Reading first = null;
        Reading last = null;
        for (Reading reading : trusted) {
            if (!reading.at().isAfter(start)) {
                first = reading;
            }
            if (!reading.at().isAfter(end)) {
                last = reading;
            }
        }
        if (first == null) {
            first = trusted.stream()
                    .filter(reading -> reading.at().isAfter(start) && !reading.at().isAfter(end))
                    .findFirst()
                    .orElse(null);
        }
        if (first == null || last == null || first == last || !last.at().isAfter(first.at())) {
            return null;
        }
        return new KmSpan(last.km() - first.km(), first.at().isAfter(start) ? first.at() : start);
    }

    private static BigDecimal perKm(BigDecimal amount, Long km) {
        return km == null || km <= 0 ? null : amount.divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<ExpenseDto> expenses) {
        return expenses.stream().map(ExpenseDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * El promedio de la flota. El costo por km de la flota no es el promedio de los costos por km de cada
     * vehículo, sino el gasto de los que tienen km dividido por sus km: así un vehículo con pocos km y un
     * gasto grande no sube el de toda la flota hasta que ningún otro quede por encima.
     *
     * @param round a dos decimales la plata y a uno el resto, para la respuesta; sin redondear para la marca
     */
    static Averages averages(List<Row> rows, boolean round) {
        List<Row> withKm = rows.stream().filter(row -> row.kmDriven() != null && row.kmDriven() > 0).toList();
        long fleetKm = withKm.stream().mapToLong(Row::kmDriven).sum();
        return new Averages(averageMoney(rows, Row::maintenanceCost, round), averageMoney(rows, Row::operatingCost, round),
                averageMoney(rows, Row::totalCost, round),
                average(rows, row -> row.kmDriven() == null ? null : row.kmDriven().doubleValue(), round),
                fleetPerKm(withKm, Row::maintenanceCost, fleetKm, round), fleetPerKm(withKm, Row::totalCost, fleetKm, round),
                average(rows, Row::availabilityPct, round), average(rows, Row::downtimeHours, round),
                average(rows, row -> (double) row.stops(), round),
                average(rows, row -> (double) row.recurringControls(), round));
    }

    private static BigDecimal fleetPerKm(List<Row> withKm, Function<Row, BigDecimal> cost, long fleetKm, boolean round) {
        if (withKm.isEmpty() || fleetKm <= 0) {
            return null;
        }
        BigDecimal total = withKm.stream().map(cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(fleetKm), round ? 2 : 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageMoney(List<Row> rows, Function<Row, BigDecimal> value, boolean round) {
        List<BigDecimal> values = rows.stream().map(value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), round ? 2 : 10, RoundingMode.HALF_UP);
    }

    private static Double average(List<Row> rows, Function<Row, Double> value, boolean round) {
        List<Double> values = rows.stream().map(value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return round ? Math.round(mean * 10) / 10.0 : mean;
    }

    /**
     * "Para revisar": peor que el promedio de la flota en al menos {@link #MIN_REASONS} de estos tres:
     * costo por km de mantenimiento (más alto), disponibilidad (más baja) y controles con fallas
     * recurrentes (más). Un indicador sin dato, en el vehículo o en el promedio, no cuenta.
     */
    static Review review(Row row, Averages fleet) {
        List<String> reasons = new ArrayList<>();
        if (row.maintenanceCostPerKm() != null && fleet.maintenanceCostPerKm() != null
                && row.maintenanceCostPerKm().compareTo(fleet.maintenanceCostPerKm()) > 0) {
            reasons.add(REASON_COST_PER_KM);
        }
        if (row.availabilityPct() != null && fleet.availabilityPct() != null
                && row.availabilityPct() < fleet.availabilityPct()) {
            reasons.add(REASON_AVAILABILITY);
        }
        if (fleet.recurringControls() != null && row.recurringControls() > fleet.recurringControls()) {
            reasons.add(REASON_RECURRING);
        }
        return new Review(reasons.size() >= MIN_REASONS, reasons.size() >= MIN_REASONS ? reasons : List.of());
    }

    private static Row withReview(Row row, Review review) {
        return new Row(row.vehicleId(), row.plate(), row.brand(), row.model(), row.vehicleType(), row.year(),
                row.maintenanceCost(), row.operatingCost(), row.totalCost(), row.kmDriven(), row.maintenanceCostPerKm(),
                row.totalCostPerKm(), row.availabilityPct(), row.downtimeHours(), row.stops(), row.recurringControls(),
                review);
    }
}
