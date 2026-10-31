package org.fleetguard.service;

import org.fleetguard.dto.DowntimeDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * CAM-134: cuánto tiempo estuvo cada vehículo fuera de servicio (con al menos un defecto bloqueante abierto)
 * y cuánto tarda en promedio una reparación. Ver docs/api/CAM-134-downtime-contract.md.
 */
@Service
public class DowntimeService {

    private final VehicleRepository vehicleRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final Clock clock;

    public DowntimeService(VehicleRepository vehicleRepository, DefectRepository defectRepository,
                           WorkOrderRepository workOrderRepository, Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.clock = clock;
    }

    public DowntimeDto get(String fromParam, String toParam, String vehicleIdParam) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (fromParam == null || fromParam.isBlank()) {
            details.add(new FieldValidationErrorDetail("from", "Obligatorio"));
        }
        if (toParam == null || toParam.isBlank()) {
            details.add(new FieldValidationErrorDetail("to", "Obligatorio"));
        }
        DateRange range = DateRange.parse(fromParam, toParam, details);
        if (details.isEmpty() && !range.to().isAfter(range.from())) {
            details.add(new FieldValidationErrorDetail("to", "Tiene que ser posterior a from"));
        }
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Período inválido", details);
        }

        List<Vehicle> vehicles = vehicleIdParam != null && !vehicleIdParam.isBlank()
                ? List.of(Uuids.parse(vehicleIdParam).flatMap(vehicleRepository::findById)
                        .orElseThrow(() -> new VehicleNotFoundException(vehicleIdParam)))
                : vehicleRepository.findByActive(true);
        Set<UUID> vehicleIds = vehicles.stream().map(Vehicle::getId).collect(Collectors.toSet());

        // §3: lo que todavía no pasó no cuenta.
        Instant now = clock.instant();
        Instant from = range.from();
        Instant end = range.to().isAfter(now) ? now : range.to();
        if (end.isBefore(from)) {
            end = from;
        }

        Map<UUID, List<Interval>> outOfService = outOfServiceIntervals(vehicleIds, from, end, now);
        Map<UUID, List<WorkOrder>> repairsByVehicle = workOrderRepository.findFinalizedBetween(from, end).stream()
                .filter(workOrder -> vehicleIds.contains(workOrder.getVehicleId()))
                .collect(Collectors.groupingBy(WorkOrder::getVehicleId));

        long periodSeconds = Duration.between(from, end).toSeconds();
        List<DowntimeDto.Vehicle> rows = new ArrayList<>();
        long totalDownSeconds = 0;
        int totalStops = 0;
        for (Vehicle vehicle : vehicles) {
            List<Interval> inPeriod = clip(merge(outOfService.getOrDefault(vehicle.getId(), List.of())), from, end);
            long downSeconds = inPeriod.stream().mapToLong(Interval::seconds).sum();
            totalDownSeconds += downSeconds;
            totalStops += inPeriod.size();
            rows.add(new DowntimeDto.Vehicle(vehicle.getId().toString(), vehicle.getPlate(),
                    availability(downSeconds, periodSeconds), hours(downSeconds), inPeriod.size(),
                    repairTimes(repairsByVehicle.getOrDefault(vehicle.getId(), List.of()))));
        }
        rows.sort(Comparator.comparingDouble(DowntimeDto.Vehicle::downtimeHours).reversed()
                .thenComparing(DowntimeDto.Vehicle::plate));

        List<WorkOrder> allRepairs = repairsByVehicle.values().stream().flatMap(List::stream).toList();
        DowntimeDto.Fleet fleet = new DowntimeDto.Fleet(
                vehicles.isEmpty() ? null : availability(totalDownSeconds, periodSeconds * vehicles.size()),
                hours(totalDownSeconds), totalStops, repairTimes(allRepairs));
        return new DowntimeDto(from, end, fleet, rows);
    }

    /**
     * Por vehículo, el intervalo de cada defecto bloqueante: de que se reportó a que se resolvió, o hasta ahora si
     * sigue abierto. Los resueltos antes de CAM-134 no tienen resolvedAt y usan la finalización de su OT (§2).
     */
    private Map<UUID, List<Interval>> outOfServiceIntervals(Set<UUID> vehicleIds, Instant from, Instant end,
                                                           Instant now) {
        List<Defect> blocking = defectRepository.findBlockingInPeriodWithInspection(from, end).stream()
                .filter(defect -> vehicleIds.contains(vehicleId(defect)))
                .toList();

        List<UUID> legacyIds = blocking.stream()
                .filter(defect -> !"open".equals(defect.getStatus()) && defect.getResolvedAt() == null)
                .map(Defect::getId)
                .toList();
        Map<UUID, Instant> legacyResolvedAt = legacyIds.isEmpty() ? Map.of()
                : workOrderRepository.findFinalizedByDefectIds(legacyIds).stream()
                        .filter(workOrder -> workOrder.getFinalizedAt() != null)
                        .collect(Collectors.toMap(WorkOrder::getDefectId, WorkOrder::getFinalizedAt,
                                BinaryOperator.minBy(Comparator.naturalOrder())));

        Map<UUID, List<Interval>> byVehicle = new HashMap<>();
        for (Defect defect : blocking) {
            Instant stop = "open".equals(defect.getStatus()) ? now
                    : defect.getResolvedAt() != null ? defect.getResolvedAt()
                    : legacyResolvedAt.get(defect.getId());
            if (stop == null || !stop.isAfter(defect.getCreatedAt())) {
                continue;
            }
            byVehicle.computeIfAbsent(vehicleId(defect), id -> new ArrayList<>())
                    .add(new Interval(defect.getCreatedAt(), stop));
        }
        return byVehicle;
    }

    /** Une los intervalos que se solapan o se tocan: dos bloqueantes a la vez son una sola parada (§1). */
    static List<Interval> merge(List<Interval> intervals) {
        List<Interval> sorted = intervals.stream().sorted(Comparator.comparing(Interval::start)).toList();
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : sorted) {
            Interval last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && !interval.start().isAfter(last.stop())) {
                merged.set(merged.size() - 1,
                        new Interval(last.start(), interval.stop().isAfter(last.stop()) ? interval.stop() : last.stop()));
            } else {
                merged.add(interval);
            }
        }
        return merged;
    }

    /** Las paradas ya unidas, recortadas al período; las que no lo tocan quedan afuera (§4). */
    static List<Interval> clip(List<Interval> intervals, Instant from, Instant end) {
        return intervals.stream()
                .filter(interval -> interval.stop().isAfter(from) && interval.start().isBefore(end))
                .map(interval -> new Interval(interval.start().isBefore(from) ? from : interval.start(),
                        interval.stop().isAfter(end) ? end : interval.stop()))
                .toList();
    }

    private static DowntimeDto.RepairTimes repairTimes(List<WorkOrder> finalized) {
        return new DowntimeDto.RepairTimes(averageHours(finalized), finalized.size(),
                average(finalized, WorkOrderExecutionType.INTERNO), average(finalized, WorkOrderExecutionType.EXTERNO));
    }

    private static DowntimeDto.Average average(List<WorkOrder> finalized, WorkOrderExecutionType type) {
        List<WorkOrder> ofType = finalized.stream().filter(workOrder -> workOrder.getExecutionType() == type).toList();
        return new DowntimeDto.Average(averageHours(ofType), ofType.size());
    }

    private static Double averageHours(List<WorkOrder> finalized) {
        if (finalized.isEmpty()) {
            return null;
        }
        double seconds = finalized.stream()
                .mapToLong(workOrder -> Duration.between(workOrder.getCreatedAt(), workOrder.getFinalizedAt()).toSeconds())
                .average()
                .orElse(0);
        return hours(Math.round(seconds));
    }

    /** §5: con un decimal; null si el período no tiene duración. */
    private static Double availability(long downSeconds, long periodSeconds) {
        if (periodSeconds <= 0) {
            return null;
        }
        return Math.round(1000.0 * (periodSeconds - downSeconds) / periodSeconds) / 10.0;
    }

    private static double hours(long seconds) {
        return Math.round(seconds / 360.0) / 10.0;
    }

    private static UUID vehicleId(Defect defect) {
        return defect.getInspectionAnswer().getInspection().getVehicleId();
    }

    record Interval(Instant start, Instant stop) {

        long seconds() {
            return Duration.between(start, stop).toSeconds();
        }
    }
}
