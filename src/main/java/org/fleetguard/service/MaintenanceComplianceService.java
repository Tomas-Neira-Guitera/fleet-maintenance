package org.fleetguard.service;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.MaintenanceComplianceDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-155: qué tan al día está la flota con el mantenimiento. Ver docs/api/CAM-155-maintenance-compliance-contract.md.
 */
@Service
public class MaintenanceComplianceService {

    private static final List<WorkOrderStatus> OPEN_STATUSES = List.of(WorkOrderStatus.ASIGNADA, WorkOrderStatus.EN_PROCESO);

    private final MaintenanceCompletionRepository completionRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final Clock clock;

    public MaintenanceComplianceService(MaintenanceCompletionRepository completionRepository,
                                        DefectRepository defectRepository, WorkOrderRepository workOrderRepository,
                                        Clock clock) {
        this.completionRepository = completionRepository;
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MaintenanceComplianceDto get(String fromParam, String toParam) {
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

        Instant now = clock.instant();
        return new MaintenanceComplianceDto(range.from(), range.to(), preventive(range),
                defectResolution(range.from(), range.to().isAfter(now) ? now : range.to()), openWorkOrders(now));
    }

    /**
     * Cada mantenimiento hecho en el período se compara con cuándo vencía: el anterior del mismo plan en el mismo
     * vehículo más el intervalo del plan. El primero de cada plan no tiene anterior y no cuenta.
     */
    private MaintenanceComplianceDto.Preventive preventive(DateRange range) {
        LocalDate fromDate = LocalDate.ofInstant(range.from(), ZoneOffset.UTC);
        LocalDate toDate = LocalDate.ofInstant(range.to(), ZoneOffset.UTC);
        Map<UUID, List<MaintenanceCompletion>> byAssignment = completionRepository.findCompletedBeforeWithPlan(toDate)
                .stream()
                .collect(Collectors.groupingBy(completion -> completion.getAssignment().getId()));

        int onTime = 0;
        int late = 0;
        for (List<MaintenanceCompletion> completions : byAssignment.values()) {
            List<MaintenanceCompletion> ordered = completions.stream()
                    .sorted(Comparator.comparing(MaintenanceCompletion::getCompletedAt))
                    .toList();
            for (int i = 1; i < ordered.size(); i++) {
                MaintenanceCompletion done = ordered.get(i);
                if (done.getCompletedAt().isBefore(fromDate)) {
                    continue;
                }
                Boolean wasOnTime = onTime(ordered.get(i - 1), done);
                if (wasOnTime == null) {
                    continue;
                }
                if (wasOnTime) {
                    onTime++;
                } else {
                    late++;
                }
            }
        }
        Double pct = onTime + late == 0 ? null : round1(100.0 * onTime / (onTime + late));
        return new MaintenanceComplianceDto.Preventive(onTime, late, pct);
    }

    /** null si no se puede saber cuándo vencía (sin intervalo aplicable o sin los km de los dos). */
    static Boolean onTime(MaintenanceCompletion previous, MaintenanceCompletion done) {
        MaintenancePlan plan = done.getAssignment().getMaintenancePlan();
        LocalDate dueDate = plan.getIntervalDays() == null ? null
                : previous.getCompletedAt().plusDays(plan.getIntervalDays());
        Long dueKm = plan.getIntervalKm() == null || previous.getCompletedKm() == null || done.getCompletedKm() == null
                ? null : previous.getCompletedKm() + plan.getIntervalKm();
        if (dueDate == null && dueKm == null) {
            return null;
        }
        boolean dateOk = dueDate == null || !done.getCompletedAt().isAfter(dueDate);
        boolean kmOk = dueKm == null || done.getCompletedKm() <= dueKm;
        return dateOk && kmOk;
    }

    private MaintenanceComplianceDto.DefectResolution defectResolution(Instant from, Instant to) {
        List<Defect> resolved = to.isAfter(from) ? defectRepository.findResolvedBetween(from, to) : List.of();
        Double avgHours = resolved.isEmpty() ? null : round1(resolved.stream()
                .mapToLong(defect -> Duration.between(defect.getCreatedAt(), defect.getResolvedAt()).toMinutes())
                .average()
                .orElse(0) / 60.0);
        return new MaintenanceComplianceDto.DefectResolution(resolved.size(), avgHours);
    }

    private MaintenanceComplianceDto.OpenWorkOrders openWorkOrders(Instant now) {
        int under7 = 0;
        int from7To30 = 0;
        int over30 = 0;
        List<WorkOrder> open = workOrderRepository.findByStatusIn(OPEN_STATUSES);
        for (WorkOrder workOrder : open) {
            long days = Duration.between(workOrder.getCreatedAt(), now).toDays();
            if (days < 7) {
                under7++;
            } else if (days <= 30) {
                from7To30++;
            } else {
                over30++;
            }
        }
        return new MaintenanceComplianceDto.OpenWorkOrders(open.size(), under7, from7To30, over30);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
