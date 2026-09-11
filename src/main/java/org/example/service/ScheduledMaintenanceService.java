package org.example.service;

import org.example.dto.CreateScheduleRequest;
import org.example.dto.FieldValidationErrorDetail;
import org.example.dto.ScheduleDto;
import org.example.dto.UpdateScheduleRequest;
import org.example.entity.Defect;
import org.example.entity.ScheduleSourceType;
import org.example.entity.ScheduleStatus;
import org.example.entity.ScheduledMaintenance;
import org.example.entity.Vehicle;
import org.example.entity.VehicleMaintenanceAssignment;
import org.example.exception.AssignmentNotFoundException;
import org.example.exception.DefectNotFoundException;
import org.example.exception.MaintenanceConflictException;
import org.example.exception.MaintenanceValidationException;
import org.example.exception.ScheduleNotFoundException;
import org.example.exception.VehicleNotFoundException;
import org.example.repository.DefectRepository;
import org.example.repository.ScheduledMaintenanceRepository;
import org.example.repository.VehicleMaintenanceAssignmentRepository;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Cuándo lo vamos a hacer" -- distinto de nextDue* (calculado) y de MaintenanceCompletion
 * (historial de cuándo ya se hizo). Ver CAM-42-programacion-mantenimientos.md. Un único
 * punto de entrada genérico cubre CAM-50 (origen assignment), CAM-51 (origen defect) y
 * la programación suelta desde el calendario (origen manual, sin plan ni defecto).
 */
@Service
public class ScheduledMaintenanceService {

    private final ScheduledMaintenanceRepository scheduleRepository;
    private final VehicleMaintenanceAssignmentRepository assignmentRepository;
    private final DefectRepository defectRepository;
    private final VehicleRepository vehicleRepository;

    public ScheduledMaintenanceService(ScheduledMaintenanceRepository scheduleRepository,
                                        VehicleMaintenanceAssignmentRepository assignmentRepository,
                                        DefectRepository defectRepository,
                                        VehicleRepository vehicleRepository) {
        this.scheduleRepository = scheduleRepository;
        this.assignmentRepository = assignmentRepository;
        this.defectRepository = defectRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public record CreateResult(ScheduleDto dto, boolean created) {
    }

    @Transactional
    public CreateResult create(CreateScheduleRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();

        ScheduleSourceType sourceType = ScheduleSourceType.fromJson(request.sourceType());
        if (sourceType == null) {
            details.add(new FieldValidationErrorDetail("sourceType", "Debe ser 'assignment', 'defect' o 'manual'"));
        }
        boolean manual = sourceType == ScheduleSourceType.MANUAL;
        if (!manual && (request.sourceId() == null || request.sourceId().isBlank())) {
            details.add(new FieldValidationErrorDetail("sourceId", "Obligatorio para sourceType assignment/defect"));
        }
        if (manual && (request.vehicleId() == null || request.vehicleId().isBlank())) {
            details.add(new FieldValidationErrorDetail("vehicleId", "Obligatorio para sourceType manual"));
        }
        if (manual && (request.title() == null || request.title().isBlank())) {
            details.add(new FieldValidationErrorDetail("title", "Obligatorio para sourceType manual"));
        }
        Instant scheduledAt = parseInstant(request.scheduledAt(), "scheduledAt", details);
        if (scheduledAt != null && scheduledAt.isBefore(Instant.now())) {
            details.add(new FieldValidationErrorDetail("scheduledAt", "PAST_DATE"));
        }
        if (!details.isEmpty()) {
            throw new MaintenanceValidationException("Datos inválidos para programar el mantenimiento", details);
        }

        UUID vehicleId;
        UUID assignmentId = null;
        UUID defectId = null;
        String title;

        if (sourceType == ScheduleSourceType.ASSIGNMENT) {
            VehicleMaintenanceAssignment assignment = assignmentRepository.findById(UUID.fromString(request.sourceId()))
                    .orElseThrow(() -> new AssignmentNotFoundException(request.sourceId()));
            if (!assignment.isActive()) {
                throw new MaintenanceConflictException("ASSIGNMENT_INACTIVE",
                        "No se puede programar una asignación desactivada");
            }
            vehicleId = assignment.getVehicleId();
            assignmentId = assignment.getId();
            title = assignment.getMaintenancePlan().getName();
        } else if (sourceType == ScheduleSourceType.DEFECT) {
            Defect defect = defectRepository.findById(UUID.fromString(request.sourceId()))
                    .orElseThrow(() -> new DefectNotFoundException(request.sourceId()));
            if (!"open".equals(defect.getStatus())) {
                throw new MaintenanceConflictException("DEFECT_RESOLVED",
                        "No se puede programar un defecto ya resuelto");
            }
            vehicleId = defect.getInspectionAnswer().getInspection().getVehicleId();
            defectId = defect.getId();
            title = defect.getDescription();
        } else {
            // Manual: no hay plan ni defecto -- el cliente manda vehicleId y title directo.
            Vehicle manualVehicle = vehicleRepository.findById(UUID.fromString(request.vehicleId()))
                    .orElseThrow(() -> new VehicleNotFoundException(request.vehicleId()));
            vehicleId = manualVehicle.getId();
            title = request.title();
        }

        // Manual nunca deduplica -- no hay un origen único del que solo pueda existir una
        // programación activa a la vez, a diferencia de assignment/defect.
        Optional<ScheduledMaintenance> existing = assignmentId != null
                ? scheduleRepository.findFirstByAssignmentIdAndStatus(assignmentId, ScheduleStatus.SCHEDULED)
                : defectId != null
                    ? scheduleRepository.findFirstByDefectIdAndStatus(defectId, ScheduleStatus.SCHEDULED)
                    : Optional.empty();

        boolean created = existing.isEmpty();
        ScheduledMaintenance schedule;
        if (existing.isPresent()) {
            schedule = existing.get();
            schedule.reschedule(scheduledAt);
        } else {
            schedule = new ScheduledMaintenance(vehicleId, sourceType, assignmentId, defectId, title, scheduledAt);
        }
        if (request.notes() != null) {
            schedule.setNotes(request.notes());
        }
        scheduleRepository.save(schedule);

        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
        return new CreateResult(toDto(schedule, vehicle == null ? null : vehicle.getPlate()), created);
    }

    @Transactional
    public ScheduleDto update(String id, UpdateScheduleRequest request) {
        ScheduledMaintenance schedule = scheduleRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ScheduleNotFoundException(id));

        if (request.status() != null) {
            ScheduleStatus newStatus = ScheduleStatus.fromJson(request.status());
            if (newStatus == null) {
                throw new MaintenanceValidationException("Estado inválido",
                        List.of(new FieldValidationErrorDetail("status", "Debe ser 'done' o 'cancelled'")));
            }
            if (newStatus == ScheduleStatus.DONE && schedule.getSourceType() == ScheduleSourceType.ASSIGNMENT) {
                throw new MaintenanceConflictException("USE_COMPLETION_ENDPOINT",
                        "Un mantenimiento programado desde un plan se marca como realizado registrando el completion de la asignación, no acá");
            }
            if (newStatus == ScheduleStatus.DONE) {
                schedule.markDone();
            } else if (newStatus == ScheduleStatus.CANCELLED) {
                schedule.cancel();
            }
        }
        if (request.scheduledAt() != null) {
            List<FieldValidationErrorDetail> details = new ArrayList<>();
            Instant parsed = parseInstant(request.scheduledAt(), "scheduledAt", details);
            if (!details.isEmpty()) {
                throw new MaintenanceValidationException("Fecha inválida", details);
            }
            schedule.reschedule(parsed);
        }

        scheduleRepository.save(schedule);
        Vehicle vehicle = vehicleRepository.findById(schedule.getVehicleId()).orElse(null);
        return toDto(schedule, vehicle == null ? null : vehicle.getPlate());
    }

    public List<ScheduleDto> listByRange(String from, String to, String statusParam, String vehicleIdParam) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        Instant fromInstant = parseInstant(from, "from", details);
        Instant toInstant = parseInstant(to, "to", details);
        if (!details.isEmpty()) {
            throw new MaintenanceValidationException("Rango de fechas inválido", details);
        }
        ScheduleStatus status = statusParam == null ? ScheduleStatus.SCHEDULED : ScheduleStatus.fromJson(statusParam);
        if (status == null) {
            throw new MaintenanceValidationException("Estado inválido",
                    List.of(new FieldValidationErrorDetail("status", "Debe ser 'scheduled', 'done' o 'cancelled'")));
        }

        List<ScheduledMaintenance> schedules;
        if (vehicleIdParam != null && !vehicleIdParam.isBlank()) {
            schedules = scheduleRepository.findByVehicleIdAndScheduledAtBetweenAndStatusOrderByScheduledAtAsc(
                    UUID.fromString(vehicleIdParam), fromInstant, toInstant, status);
        } else {
            schedules = scheduleRepository.findByScheduledAtBetweenAndStatusOrderByScheduledAtAsc(fromInstant, toInstant, status);
        }

        Map<UUID, String> plateByVehicleId = vehicleRepository
                .findAllById(schedules.stream().map(ScheduledMaintenance::getVehicleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Vehicle::getPlate));

        return schedules.stream().map(s -> toDto(s, plateByVehicleId.get(s.getVehicleId()))).toList();
    }

    /** Cierra automáticamente la programación activa de una asignación al registrar su completion. */
    @Transactional
    public void closeActiveScheduleForAssignment(UUID assignmentId) {
        scheduleRepository.findFirstByAssignmentIdAndStatus(assignmentId, ScheduleStatus.SCHEDULED)
                .ifPresent(schedule -> {
                    schedule.markDone();
                    scheduleRepository.save(schedule);
                });
    }

    private ScheduleDto toDto(ScheduledMaintenance schedule, String plate) {
        return new ScheduleDto(
                schedule.getId().toString(),
                schedule.getVehicleId().toString(),
                plate,
                schedule.getSourceType().toJson(),
                schedule.getAssignmentId() == null ? null : schedule.getAssignmentId().toString(),
                schedule.getDefectId() == null ? null : schedule.getDefectId().toString(),
                schedule.getTitle(),
                schedule.getScheduledAt().toString(),
                schedule.getStatus().toJson(),
                schedule.getNotes()
        );
    }

    /** Acepta timestamp ISO completo (con hora) o solo fecha (YYYY-MM-DD, se toma como inicio del día UTC). */
    private Instant parseInstant(String value, String field, List<FieldValidationErrorDetail> details) {
        if (value == null) {
            details.add(new FieldValidationErrorDetail(field, "Obligatorio"));
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e2) {
                details.add(new FieldValidationErrorDetail(field, "Fecha/hora inválida, formato esperado ISO-8601"));
                return null;
            }
        }
    }
}
