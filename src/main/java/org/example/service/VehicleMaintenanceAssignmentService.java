package org.example.service;

import org.example.dto.AssignmentDto;
import org.example.dto.CreateAssignmentRequest;
import org.example.dto.FieldValidationErrorDetail;
import org.example.dto.UpdateAssignmentRequest;
import org.example.entity.IntervalType;
import org.example.entity.MaintenancePlan;
import org.example.entity.MaintenanceStatus;
import org.example.entity.Vehicle;
import org.example.entity.VehicleMaintenanceAssignment;
import org.example.exception.AssignmentNotFoundException;
import org.example.exception.MaintenanceConflictException;
import org.example.exception.MaintenanceValidationException;
import org.example.exception.VehicleNotFoundException;
import org.example.repository.VehicleMaintenanceAssignmentRepository;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Operaciones sobre la relación vehículo↔plan -- expuestas siempre bajo
 * /api/vehicles/{vehicleId}/maintenance-assignments, nunca como recurso propio
 * (ver CAM-40-maintenance-api-contract.md, división de recursos).
 */
@Service
public class VehicleMaintenanceAssignmentService {

    private final VehicleMaintenanceAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final MaintenancePlanService maintenancePlanService;

    public VehicleMaintenanceAssignmentService(VehicleMaintenanceAssignmentRepository assignmentRepository,
                                                VehicleRepository vehicleRepository,
                                                MaintenancePlanService maintenancePlanService) {
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.maintenancePlanService = maintenancePlanService;
    }

    public List<AssignmentDto> list(String vehicleId, boolean active) {
        Vehicle vehicle = findVehicle(vehicleId);
        List<VehicleMaintenanceAssignment> assignments = assignmentRepository.findByVehicleIdAndActive(vehicle.getId(), active);
        return assignments.stream().map(a -> toDto(a, vehicle.getOdometerKm())).toList();
    }

    @Transactional
    public AssignmentDto create(String vehicleId, CreateAssignmentRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);
        MaintenancePlan plan = maintenancePlanService.find(request.maintenancePlanId());

        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (!plan.isActive()) {
            details.add(new FieldValidationErrorDetail("maintenancePlanId", "El plan está desactivado en el catálogo"));
        }

        Long lastDoneKm = request.lastDoneKm();
        LocalDate lastDoneDate = parseOptionalDate(request.lastDoneDate(), "lastDoneDate", details);

        boolean needsKm = plan.getIntervalType() == IntervalType.KM || plan.getIntervalType() == IntervalType.BOTH;
        boolean needsDate = plan.getIntervalType() == IntervalType.TIME || plan.getIntervalType() == IntervalType.BOTH;

        // Si se omiten ambos, se siembra con el estado actual del vehículo (punto de partida conservador).
        boolean omittedBoth = lastDoneKm == null && request.lastDoneDate() == null;
        if (omittedBoth) {
            lastDoneKm = needsKm ? vehicle.getOdometerKm() : null;
            lastDoneDate = needsDate ? LocalDate.now() : null;
        } else {
            if (needsKm && lastDoneKm == null) {
                details.add(new FieldValidationErrorDetail("lastDoneKm", "Obligatorio para este tipo de plan"));
            }
            // Si lastDoneDate quedó null por un error de parseo, ese error ya está en `details`
            // (agregado por parseOptionalDate) -- acá solo cubrimos el caso de que directamente no viniera.
            if (needsDate && lastDoneDate == null && request.lastDoneDate() == null) {
                details.add(new FieldValidationErrorDetail("lastDoneDate", "Obligatorio para este tipo de plan"));
            }
        }

        Optional<VehicleMaintenanceAssignment> activeExisting =
                assignmentRepository.findFirstByVehicleIdAndMaintenancePlan_IdAndActiveTrue(vehicle.getId(), plan.getId());
        if (activeExisting.isPresent()) {
            throw new MaintenanceConflictException("DUPLICATE_ACTIVE_ASSIGNMENT",
                    "Este vehículo ya tiene una asignación activa de este plan");
        }

        if (!details.isEmpty()) {
            throw new MaintenanceValidationException("Datos inválidos para asignar el plan", details);
        }

        // Si había una asignación inactiva del mismo plan, se reactiva en vez de duplicar el historial.
        VehicleMaintenanceAssignment assignment = assignmentRepository
                .findFirstByVehicleIdAndMaintenancePlan_IdAndActiveFalse(vehicle.getId(), plan.getId())
                .orElseGet(() -> new VehicleMaintenanceAssignment(vehicle.getId(), plan, null, null));

        assignment.setLastDoneKm(lastDoneKm);
        assignment.setLastDoneDate(lastDoneDate);
        assignment.setActive(true);
        assignment.recalculateNextDue();

        return toDto(assignmentRepository.save(assignment), vehicle.getOdometerKm());
    }

    @Transactional
    public AssignmentDto update(String vehicleId, String assignmentId, UpdateAssignmentRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);
        VehicleMaintenanceAssignment assignment = findAssignment(vehicle.getId(), assignmentId);

        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.lastDoneKm() != null) {
            assignment.setLastDoneKm(request.lastDoneKm());
        }
        if (request.lastDoneDate() != null) {
            LocalDate parsed = parseOptionalDate(request.lastDoneDate(), "lastDoneDate", details);
            if (!details.isEmpty()) {
                throw new MaintenanceValidationException("Fecha inválida", details);
            }
            assignment.setLastDoneDate(parsed);
        }
        assignment.recalculateNextDue();
        if (request.active() != null) {
            assignment.setActive(request.active());
        }

        return toDto(assignmentRepository.save(assignment), vehicle.getOdometerKm());
    }

    @Transactional
    public void delete(String vehicleId, String assignmentId) {
        Vehicle vehicle = findVehicle(vehicleId);
        VehicleMaintenanceAssignment assignment = findAssignment(vehicle.getId(), assignmentId);
        assignment.setActive(false);
        assignmentRepository.save(assignment);
    }

    VehicleMaintenanceAssignment findAssignment(UUID vehicleId, String assignmentId) {
        return assignmentRepository.findByIdAndVehicleId(UUID.fromString(assignmentId), vehicleId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
    }

    Vehicle findVehicle(String vehicleId) {
        return vehicleRepository.findById(UUID.fromString(vehicleId))
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    }

    AssignmentDto toDto(VehicleMaintenanceAssignment assignment, long currentKm) {
        MaintenanceStatus status = MaintenanceStatusCalculator.computeStatus(
                assignment.getNextDueKm(), assignment.getNextDueDate(), currentKm, LocalDate.now());
        return new AssignmentDto(
                assignment.getId().toString(),
                assignment.getVehicleId().toString(),
                assignment.getMaintenancePlan().getId().toString(),
                assignment.getMaintenancePlan().getName(),
                assignment.getMaintenancePlan().getIntervalType().toJson(),
                assignment.getLastDoneKm(),
                assignment.getLastDoneDate() == null ? null : assignment.getLastDoneDate().toString(),
                assignment.getNextDueKm(),
                assignment.getNextDueDate() == null ? null : assignment.getNextDueDate().toString(),
                status.toJson(),
                assignment.isActive()
        );
    }

    private LocalDate parseOptionalDate(String value, String field, List<FieldValidationErrorDetail> details) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            details.add(new FieldValidationErrorDetail(field, "Fecha inválida, formato esperado YYYY-MM-DD"));
            return null;
        }
    }
}
