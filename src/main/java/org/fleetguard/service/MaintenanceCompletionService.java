package org.fleetguard.service;

import org.fleetguard.dto.AssignmentSummaryDto;
import org.fleetguard.dto.CompletionDto;
import org.fleetguard.dto.CompletionResultDto;
import org.fleetguard.dto.CreateCompletionRequest;
import org.fleetguard.dto.DocumentInputDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.MaintenanceStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.MaintenanceConflictException;
import org.fleetguard.exception.MaintenanceValidationException;
import org.fleetguard.mapper.MaintenanceCompletionMapper;
import org.fleetguard.repository.MaintenanceCompletionRepository;
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
    private final ScheduledMaintenanceService scheduledMaintenanceService;
    private final OdometerReadings odometerReadings;
    private final LinkedDocumentDueDates linkedDueDates;
    private final VehicleDocumentWriter documentWriter;

    public MaintenanceCompletionService(MaintenanceCompletionRepository completionRepository,
                                         VehicleMaintenanceAssignmentService assignmentService,
                                         MaintenanceCompletionMapper mapper,
                                         ScheduledMaintenanceService scheduledMaintenanceService,
                                         OdometerReadings odometerReadings, LinkedDocumentDueDates linkedDueDates,
                                         VehicleDocumentWriter documentWriter) {
        this.completionRepository = completionRepository;
        this.assignmentService = assignmentService;
        this.mapper = mapper;
        this.scheduledMaintenanceService = scheduledMaintenanceService;
        this.odometerReadings = odometerReadings;
        this.linkedDueDates = linkedDueDates;
        this.documentWriter = documentWriter;
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
        VehicleDocumentType documentType = assignment.getMaintenancePlan().getDocumentType();
        VehicleDocumentWriter.Fields document = null;
        if (request.document() != null) {
            if (documentType == null) {
                details.add(new FieldValidationErrorDetail("document", "Este plan no renueva ningún documento"));
            } else {
                DocumentInputDto input = request.document();
                document = VehicleDocumentWriter.parse("document.", input.number(), null, input.expiresAt(),
                        input.fileUrl(), true, details);
                if (completedAt != null && document.expiresAt() != null && document.expiresAt().isBefore(completedAt)) {
                    details.add(new FieldValidationErrorDetail("document.expiresAt", "No puede ser anterior a la fecha del mantenimiento"));
                }
            }
        }
        if (!details.isEmpty()) {
            throw new MaintenanceValidationException("Datos inválidos para registrar el completion", details);
        }

        MaintenanceCompletion completion = new MaintenanceCompletion(assignment, completedAt, request.completedKm(),
                request.workOrderId(), request.notes());
        completionRepository.save(completion);

        if (request.completedKm() != null) {
            assignment.setLastDoneKm(request.completedKm());
            // CAM-90: el km del mantenimiento hecho (a mano o al finalizar una OT de un plan) avanza el
            // del vehículo. Va antes de calcular el estado de abajo, que ya lo usa.
            odometerReadings.apply(vehicle, request.completedKm());
        }
        assignment.setLastDoneDate(completedAt);
        linkedDueDates.recalculate(assignment);
        // CAM-127: el documento renovado que vino con el mantenimiento; su vencimiento pasa a ser el del plan.
        if (document != null) {
            documentWriter.renew(vehicle.getId(), documentType, null, new VehicleDocumentWriter.Fields(
                    document.number(), completedAt, document.expiresAt(), document.fileUrl()));
        }

        // Si había una programación activa (CAM-42) sobre esta asignación, se cierra sola --
        // el cliente no llama un segundo endpoint. Ver CAM-42-programacion-mantenimientos.md.
        scheduledMaintenanceService.closeActiveScheduleForAssignment(assignment.getId());

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
