package org.example.service;

import org.example.dto.AssignmentSummaryDto;
import org.example.dto.CompletionDto;
import org.example.dto.CompletionResultDto;
import org.example.dto.CreateCompletionRequest;
import org.example.dto.FieldValidationErrorDetail;
import org.example.entity.IntervalType;
import org.example.entity.MaintenanceCompletion;
import org.example.entity.MaintenanceStatus;
import org.example.entity.Vehicle;
import org.example.entity.VehicleMaintenanceAssignment;
import org.example.exception.MaintenanceConflictException;
import org.example.exception.MaintenanceValidationException;
import org.example.mapper.MaintenanceCompletionMapper;
import org.example.repository.MaintenanceCompletionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Historial de completions de una asignación -- ver CAM-40-maintenance-api-contract.md. */
@Service
public class MaintenanceCompletionService {

    private final MaintenanceCompletionRepository completionRepository;
    private final VehicleMaintenanceAssignmentService assignmentService;
    private final MaintenanceCompletionMapper mapper;

    public MaintenanceCompletionService(MaintenanceCompletionRepository completionRepository,
                                         VehicleMaintenanceAssignmentService assignmentService,
                                         MaintenanceCompletionMapper mapper) {
        this.completionRepository = completionRepository;
        this.assignmentService = assignmentService;
        this.mapper = mapper;
    }

    public List<CompletionDto> list(String vehicleId, String assignmentId) {
        Vehicle vehicle = assignmentService.findVehicle(vehicleId);
        VehicleMaintenanceAssignment assignment = assignmentService.findAssignment(vehicle.getId(), assignmentId);
        return completionRepository.findByAssignment_IdOrderByCompletedAtDesc(assignment.getId())
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public CompletionResultDto create(String vehicleId, String assignmentId, CreateCompletionRequest request) {
        Vehicle vehicle = assignmentService.findVehicle(vehicleId);
        VehicleMaintenanceAssignment assignment = assignmentService.findAssignment(vehicle.getId(), assignmentId);

        if (!assignment.isActive()) {
            throw new MaintenanceConflictException("ASSIGNMENT_INACTIVE",
                    "La asignación está desactivada -- reactivala antes de registrar un mantenimiento");
        }

        List<FieldValidationErrorDetail> details = new ArrayList<>();
        LocalDate completedAt = null;
        if (request.completedAt() == null) {
            details.add(new FieldValidationErrorDetail("completedAt", "Obligatorio"));
        } else {
            try {
                completedAt = LocalDate.parse(request.completedAt());
                if (completedAt.isAfter(LocalDate.now())) {
                    throw new MaintenanceValidationException("La fecha del completion no puede ser futura",
                            List.of(new FieldValidationErrorDetail("completedAt", "FUTURE_DATE")));
                }
                Optional<MaintenanceCompletion> latest =
                        completionRepository.findFirstByAssignment_IdOrderByCompletedAtDesc(assignment.getId());
                if (latest.isPresent() && completedAt.isBefore(latest.get().getCompletedAt())) {
                    throw new MaintenanceValidationException(
                            "La fecha del completion es anterior al último registrado para esta asignación",
                            List.of(new FieldValidationErrorDetail("completedAt", "OUT_OF_ORDER_COMPLETION")));
                }
            } catch (DateTimeParseException e) {
                details.add(new FieldValidationErrorDetail("completedAt", "Fecha inválida, formato esperado YYYY-MM-DD"));
            }
        }

        IntervalType intervalType = assignment.getMaintenancePlan().getIntervalType();
        boolean needsKm = intervalType == IntervalType.KM || intervalType == IntervalType.BOTH;
        if (needsKm && request.completedKm() == null) {
            details.add(new FieldValidationErrorDetail("completedKm", "Obligatorio para este tipo de plan"));
        }
        if (!details.isEmpty()) {
            throw new MaintenanceValidationException("Datos inválidos para registrar el completion", details);
        }

        MaintenanceCompletion completion = new MaintenanceCompletion(assignment, completedAt, request.completedKm(),
                request.workOrderId(), request.notes());
        completionRepository.save(completion);

        if (request.completedKm() != null) {
            assignment.setLastDoneKm(request.completedKm());
        }
        assignment.setLastDoneDate(completedAt);
        assignment.recalculateNextDue();

        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                assignment.getNextDueKm(), assignment.getNextDueDate(), vehicle.getOdometerKm(), LocalDate.now());

        return new CompletionResultDto(
                completion.getId().toString(),
                assignment.getId().toString(),
                completion.getCompletedAt().toString(),
                completion.getCompletedKm(),
                completion.getWorkOrderId(),
                completion.getNotes(),
                new AssignmentSummaryDto(
                        assignment.getId().toString(),
                        assignment.getNextDueKm(),
                        assignment.getNextDueDate() == null ? null : assignment.getNextDueDate().toString(),
                        status.toJson()
                )
        );
    }
}
