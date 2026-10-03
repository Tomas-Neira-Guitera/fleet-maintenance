package org.fleetguard.service;

import org.fleetguard.dto.CreateMaintenancePlanRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.MaintenancePlanDto;
import org.fleetguard.dto.UpdateMaintenancePlanRequest;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenancePlanNotFoundException;
import org.fleetguard.exception.MaintenanceValidationException;
import org.fleetguard.mapper.MaintenancePlanMapper;
import org.fleetguard.repository.MaintenancePlanRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lógica de negocio del catálogo /api/maintenance-plans -- ver CAM-40-maintenance-api-contract.md. */
@Service
public class MaintenancePlanService {

    private final MaintenancePlanRepository planRepository;
    private final VehicleMaintenanceAssignmentRepository assignmentRepository;
    private final MaintenancePlanMapper mapper;

    public MaintenancePlanService(MaintenancePlanRepository planRepository,
                                   VehicleMaintenanceAssignmentRepository assignmentRepository,
                                   MaintenancePlanMapper mapper) {
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
        this.mapper = mapper;
    }

    public List<MaintenancePlanDto> list(boolean active, String category) {
        List<MaintenancePlan> plans = category != null
                ? planRepository.findByActiveAndCategory(active, category)
                : planRepository.findByActive(active);
        return plans.stream().map(mapper::toDto).toList();
    }

    @Transactional
    public MaintenancePlanDto create(CreateMaintenancePlanRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.name() == null || request.name().isBlank()) {
            details.add(new FieldValidationErrorDetail("name", "El nombre es obligatorio"));
        } else if (TextLimits.exceedsTitle(request.name())) {
            details.add(new FieldValidationErrorDetail("name", TextLimits.titleTooLongMessage()));
        }
        IntervalType intervalType = IntervalType.fromJson(request.intervalType());
        if (intervalType == null) {
            details.add(new FieldValidationErrorDetail("intervalType", "Debe ser 'km', 'time' o 'both'"));
        } else {
            validateIntervalCoherence(intervalType, request.intervalKm(), request.intervalDays(), details);
        }
        if (!details.isEmpty()) {
            throw new MaintenanceValidationException("Datos inválidos para crear el plan", details);
        }

        MaintenancePlan plan = new MaintenancePlan(request.name().trim(), request.category(), intervalType,
                request.intervalKm(), request.intervalDays());
        return mapper.toDto(planRepository.save(plan));
    }

    @Transactional
    public MaintenancePlanDto update(String id, UpdateMaintenancePlanRequest request) {
        MaintenancePlan plan = find(id);

        boolean onlyReactivating = Boolean.TRUE.equals(request.active())
                && request.name() == null && request.category() == null
                && request.intervalType() == null && request.intervalKm() == null && request.intervalDays() == null;
        if (!plan.isActive() && !onlyReactivating) {
            throw new MaintenanceConflictException("PLAN_INACTIVE",
                    "El plan está desactivado -- reactivalo primero con PATCH { \"active\": true }");
        }

        if (request.name() != null) {
            if (request.name().isBlank() || TextLimits.exceedsTitle(request.name())) {
                throw new MaintenanceValidationException("Datos inválidos para editar el plan",
                        List.of(new FieldValidationErrorDetail("name", request.name().isBlank()
                                ? "El nombre es obligatorio" : TextLimits.titleTooLongMessage())));
            }
            plan.setName(request.name().trim());
        }
        if (request.category() != null) {
            plan.setCategory(request.category());
        }
        IntervalType newIntervalType = request.intervalType() != null ? IntervalType.fromJson(request.intervalType()) : plan.getIntervalType();
        Integer newIntervalKm = request.intervalKm() != null ? request.intervalKm() : plan.getIntervalKm();
        Integer newIntervalDays = request.intervalDays() != null ? request.intervalDays() : plan.getIntervalDays();

        List<FieldValidationErrorDetail> details = new ArrayList<>();
        validateIntervalCoherence(newIntervalType, newIntervalKm, newIntervalDays, details);
        if (!details.isEmpty()) {
            throw new MaintenanceValidationException("Datos inválidos para editar el plan", details);
        }
        plan.setIntervalType(newIntervalType);
        plan.setIntervalKm(newIntervalKm);
        plan.setIntervalDays(newIntervalDays);

        if (request.active() != null) {
            plan.setActive(request.active());
        }

        return mapper.toDto(planRepository.save(plan));
    }

    @Transactional
    public void delete(String id) {
        MaintenancePlan plan = find(id);
        if (assignmentRepository.existsByMaintenancePlan_Id(plan.getId())) {
            throw new MaintenanceConflictException("PLAN_IN_USE",
                    "El plan tiene asignaciones -- desactivalo en vez de borrarlo");
        }
        planRepository.delete(plan);
    }

    MaintenancePlan find(String id) {
        return planRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new MaintenancePlanNotFoundException(id));
    }

    private void validateIntervalCoherence(IntervalType type, Integer intervalKm, Integer intervalDays, List<FieldValidationErrorDetail> details) {
        if (type == null) {
            return;
        }
        boolean needsKm = type == IntervalType.KM || type == IntervalType.BOTH;
        boolean needsDays = type == IntervalType.TIME || type == IntervalType.BOTH;

        if (needsKm && (intervalKm == null || intervalKm <= 0)) {
            details.add(new FieldValidationErrorDetail("intervalKm", "Obligatorio y > 0 para intervalType " + type.toJson()));
        }
        if (!needsKm && intervalKm != null) {
            details.add(new FieldValidationErrorDetail("intervalKm", "Debe venir null para intervalType " + type.toJson()));
        }
        if (needsDays && (intervalDays == null || intervalDays <= 0)) {
            details.add(new FieldValidationErrorDetail("intervalDays", "Obligatorio y > 0 para intervalType " + type.toJson()));
        }
        if (!needsDays && intervalDays != null) {
            details.add(new FieldValidationErrorDetail("intervalDays", "Debe venir null para intervalType " + type.toJson()));
        }
    }
}
