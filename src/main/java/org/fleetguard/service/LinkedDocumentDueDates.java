package org.fleetguard.service;

import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CAM-127: un plan vinculado a un tipo de documento vence cuando vence el documento vigente del vehículo,
 * no "última vez + intervalo". Se aplica después de cada recalculateNextDue; sin documento, rige el plan.
 */
@Component
public class LinkedDocumentDueDates {

    private final VehicleDocumentRepository documentRepository;
    private final VehicleMaintenanceAssignmentRepository assignmentRepository;

    public LinkedDocumentDueDates(VehicleDocumentRepository documentRepository,
                                  VehicleMaintenanceAssignmentRepository assignmentRepository) {
        this.documentRepository = documentRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /** El vencimiento del documento vinculado, si el plan tiene vínculo y el vehículo lo tiene cargado con fecha. */
    public Optional<LocalDate> documentDueDate(VehicleMaintenanceAssignment assignment) {
        VehicleDocumentType type = assignment.getMaintenancePlan().getDocumentType();
        if (type == null) {
            return Optional.empty();
        }
        return documentRepository.findFirstByVehicleIdAndTypeAndActiveTrueAndReplacedByIdIsNull(assignment.getVehicleId(), type)
                .map(VehicleDocument::getExpiresAt);
    }

    /** Recalcula desde el plan y, si corresponde, pisa la fecha con la del documento. */
    public void recalculate(VehicleMaintenanceAssignment assignment) {
        assignment.recalculateNextDue();
        documentDueDate(assignment).ifPresent(assignment::setNextDueDate);
    }

    /** Después de cargar, renovar, corregir o borrar un documento: las asignaciones vinculadas a su tipo. */
    public void syncVehicle(UUID vehicleId, VehicleDocumentType type) {
        List<VehicleMaintenanceAssignment> linked = assignmentRepository.findByVehicleIdAndActive(vehicleId, true).stream()
                .filter(a -> a.getMaintenancePlan().getDocumentType() == type)
                .toList();
        linked.forEach(this::recalculate);
        assignmentRepository.saveAll(linked);
    }

    /** Después de vincular o desvincular un plan: todas sus asignaciones activas. */
    public void syncPlan(MaintenancePlan plan) {
        List<VehicleMaintenanceAssignment> assignments = assignmentRepository.findByMaintenancePlan_IdAndActiveTrue(plan.getId());
        assignments.forEach(this::recalculate);
        assignmentRepository.saveAll(assignments);
    }
}
