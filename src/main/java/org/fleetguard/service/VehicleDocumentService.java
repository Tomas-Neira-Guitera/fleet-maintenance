package org.fleetguard.service;

import org.fleetguard.dto.CreateCompletionRequest;
import org.fleetguard.dto.CreateVehicleDocumentRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.RenewVehicleDocumentRequest;
import org.fleetguard.dto.UpdateVehicleDocumentRequest;
import org.fleetguard.dto.VehicleDocumentDto;
import org.fleetguard.dto.VehicleDocumentsDto;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleDocument;
import org.fleetguard.entity.VehicleDocumentType;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.DocumentNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.VehicleStateConflictException;
import org.fleetguard.exception.VehicleValidationException;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleDocumentRepository;
import org.fleetguard.repository.VehicleMaintenanceAssignmentRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Documentación de un vehículo (CAM-127). Ver docs/api/CAM-127-vehicle-documents-contract.md. */
@Service
public class VehicleDocumentService {

    private final VehicleDocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleMaintenanceAssignmentRepository assignmentRepository;
    private final MaintenanceCompletionRepository completionRepository;
    private final MaintenanceCompletionService completionService;
    private final VehicleDocumentWriter writer;
    private final VehicleAvailabilityService availabilityService;
    private final NotificationSettingsService notificationSettings;

    public VehicleDocumentService(VehicleDocumentRepository documentRepository, VehicleRepository vehicleRepository,
                                  VehicleMaintenanceAssignmentRepository assignmentRepository,
                                  MaintenanceCompletionRepository completionRepository,
                                  MaintenanceCompletionService completionService, VehicleDocumentWriter writer,
                                  VehicleAvailabilityService availabilityService,
                                  NotificationSettingsService notificationSettings) {
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;
        this.completionRepository = completionRepository;
        this.completionService = completionService;
        this.writer = writer;
        this.availabilityService = availabilityService;
        this.notificationSettings = notificationSettings;
    }

    @Transactional(readOnly = true)
    public VehicleDocumentsDto list(String vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        LocalDate today = availabilityService.today();
        int leadDays = leadDays();
        List<VehicleDocument> documents = documentRepository.findByVehicleIdAndActiveTrue(vehicle.getId());

        List<VehicleDocumentDto> current = documents.stream().filter(VehicleDocument::isCurrent)
                .sorted(Comparator.comparing(VehicleDocument::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(VehicleDocument::getDisplayName))
                .map(d -> toDto(d, today, leadDays)).toList();
        List<VehicleDocumentDto> history = documents.stream().filter(d -> !d.isCurrent())
                .sorted(Comparator.comparing(VehicleDocument::getCreatedAt).reversed())
                .map(d -> toDto(d, today, leadDays)).toList();
        Set<VehicleDocumentType> loaded = documents.stream().filter(VehicleDocument::isCurrent)
                .map(VehicleDocument::getType).collect(Collectors.toSet());
        List<String> missing = VehicleDocumentType.requiredTypes().stream()
                .filter(type -> !loaded.contains(type)).map(VehicleDocumentType::toJson).toList();
        return new VehicleDocumentsDto(current, history, missing, leadDays);
    }

    @Transactional
    public VehicleDocumentDto create(String vehicleId, CreateVehicleDocumentRequest request) {
        Vehicle vehicle = findActiveVehicle(vehicleId);
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        // El nombre es libre; el tipo sale de ahí, salvo que venga explícito (por ejemplo desde Postman).
        VehicleDocumentType explicit = request.type() == null || request.type().isBlank() ? null
                : VehicleDocumentType.fromJson(request.type());
        if (request.type() != null && !request.type().isBlank() && explicit == null) {
            details.add(new FieldValidationErrorDetail("type",
                    "Debe ser 'vtv', 'seguro', 'cedula_azul', 'habilitacion_carga', 'cedula_verde', 'titulo' u 'otro'"));
        }
        String name = validateName(request.name(), explicit, details);
        VehicleDocumentType type = explicit != null ? explicit : VehicleDocumentType.infer(name);
        VehicleDocumentWriter.Fields fields = VehicleDocumentWriter.parse("", request.number(), request.issuedAt(),
                request.expiresAt(), request.fileUrl(), false, details);
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para cargar el documento", details);
        }
        rejectIfAlreadyLoaded(vehicle.getId(), type, name, null);

        VehicleDocument saved = writer.renew(vehicle.getId(), type, name, fields);
        registerLinkedMaintenance(vehicle, type, fields.issuedAt());
        return toDto(saved, availabilityService.today(), leadDays());
    }

    /** Carga el documento que reemplaza a uno vigente; el anterior pasa al historial. */
    @Transactional
    public VehicleDocumentDto renew(String vehicleId, String documentId, RenewVehicleDocumentRequest request) {
        Vehicle vehicle = findActiveVehicle(vehicleId);
        VehicleDocument previous = findCurrent(vehicle.getId(), documentId);
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        VehicleDocumentWriter.Fields fields = VehicleDocumentWriter.parse("", request.number(), request.issuedAt(),
                request.expiresAt(), request.fileUrl(), false, details);
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para renovar el documento", details);
        }

        VehicleDocument saved = writer.renewOf(previous, fields);
        registerLinkedMaintenance(vehicle, previous.getType(), fields.issuedAt());
        return toDto(saved, availabilityService.today(), leadDays());
    }

    /** Corrige un dato mal cargado del documento vigente. No cuenta como renovación. */
    @Transactional
    public VehicleDocumentDto update(String vehicleId, String documentId, UpdateVehicleDocumentRequest request) {
        Vehicle vehicle = findVehicle(vehicleId);
        VehicleDocument document = findCurrent(vehicle.getId(), documentId);
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        String name = request.name() == null ? document.getName() : validateName(request.name(), null, details);
        // Corregir el nombre puede cambiar el tipo: "Segruo" -> "Seguro" pasa a contar como el seguro.
        VehicleDocumentType previousType = document.getType();
        VehicleDocumentType type = request.name() == null || name == null ? previousType : VehicleDocumentType.infer(name);
        VehicleDocumentWriter.Fields fields = VehicleDocumentWriter.parse("",
                request.number() == null ? document.getNumber() : request.number(),
                request.issuedAt() == null ? str(document.getIssuedAt()) : request.issuedAt(),
                request.expiresAt() == null ? str(document.getExpiresAt()) : request.expiresAt(),
                request.fileUrl() == null ? document.getFileUrl() : request.fileUrl(), false, details);
        if (!details.isEmpty()) {
            throw new VehicleValidationException("Datos inválidos para corregir el documento", details);
        }
        if (request.name() != null) {
            rejectIfAlreadyLoaded(vehicle.getId(), type, name, document.getId());
        }

        document.setName(name);
        document.setType(type);
        document.setNumber(fields.number());
        document.setIssuedAt(fields.issuedAt());
        document.setExpiresAt(fields.expiresAt());
        document.setFileUrl(fields.fileUrl());
        documentRepository.save(document);
        writer.afterChange(vehicle.getId(), type);
        if (type != previousType) {
            writer.afterChange(vehicle.getId(), previousType);
        }
        return toDto(document, availabilityService.today(), leadDays());
    }

    /** Baja lógica: deja de contar (y de bloquear), pero no se borra la fila. */
    @Transactional
    public void delete(String vehicleId, String documentId) {
        Vehicle vehicle = findVehicle(vehicleId);
        VehicleDocument document = Uuids.parse(documentId)
                .flatMap(id -> documentRepository.findByIdAndVehicleIdAndActiveTrue(id, vehicle.getId()))
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        document.deactivate();
        documentRepository.save(document);
        writer.afterChange(vehicle.getId(), document.getType());
    }

    /**
     * CAM-127: cargar el documento renovado registra el mantenimiento del plan vinculado, con la fecha de
     * emisión (o hoy). No lo hace si ya hay uno registrado en esa fecha o después.
     */
    private void registerLinkedMaintenance(Vehicle vehicle, VehicleDocumentType type, LocalDate issuedAt) {
        LocalDate today = availabilityService.today();
        LocalDate doneAt = issuedAt == null || issuedAt.isAfter(today) ? today : issuedAt;
        List<VehicleMaintenanceAssignment> linked = assignmentRepository.findByVehicleIdAndActive(vehicle.getId(), true).stream()
                .filter(a -> a.getMaintenancePlan().getDocumentType() == type)
                .filter(a -> a.getMaintenancePlan().isActive() && a.getMaintenancePlan().getIntervalType() == IntervalType.TIME)
                .toList();
        for (VehicleMaintenanceAssignment assignment : linked) {
            boolean alreadyDone = completionRepository.findFirstByAssignment_IdOrderByCompletedAtDesc(assignment.getId())
                    .map(MaintenanceCompletion::getCompletedAt)
                    .filter(last -> !last.isBefore(doneAt))
                    .isPresent();
            if (!alreadyDone) {
                completionService.create(vehicle.getId().toString(), assignment.getId().toString(),
                        new CreateCompletionRequest(doneAt.toString(), null, null,
                                "Registrado al cargar el documento " + type.getLabel(), null));
            }
        }
    }

    /** Obligatorio, salvo que venga el tipo explícito: ahí se usa el nombre del tipo. */
    private static String validateName(String raw, VehicleDocumentType explicit, List<FieldValidationErrorDetail> details) {
        String name = VehicleDocumentWriter.blankToNull(raw);
        if (name == null) {
            if (explicit != null) {
                return explicit.getLabel();
            }
            details.add(new FieldValidationErrorDetail("name", "Decí qué documento es"));
        } else if (name.length() > VehicleDocumentWriter.TEXT_MAX_LENGTH) {
            details.add(new FieldValidationErrorDetail("name", "Hasta " + VehicleDocumentWriter.TEXT_MAX_LENGTH + " caracteres"));
        }
        return name;
    }

    /**
     * Un documento vigente por tipo reconocido (una sola VTV), y por nombre en los demás. El que ya está se
     * renueva o se corrige. exceptId: el que se está corrigiendo.
     */
    private void rejectIfAlreadyLoaded(UUID vehicleId, VehicleDocumentType type, String name, UUID exceptId) {
        String normalized = VehicleDocumentType.normalize(name);
        documentRepository.findByVehicleIdAndActiveTrue(vehicleId).stream()
                .filter(VehicleDocument::isCurrent)
                .filter(d -> !d.getId().equals(exceptId))
                .filter(d -> type != VehicleDocumentType.OTRO ? d.getType() == type
                        : VehicleDocumentType.normalize(d.getDisplayName()).equals(normalized))
                .findFirst()
                .ifPresent(d -> {
                    throw new VehicleStateConflictException("DOCUMENT_ALREADY_LOADED",
                            "El vehículo ya tiene cargado " + d.getDisplayName() + ": renovalo o corregilo");
                });
    }

    private int leadDays() {
        Integer lead = notificationSettings.effective(NotificationType.DOCUMENT_DUE_SOON).leadDays();
        return lead != null ? lead : NotificationCatalog.DEFAULT_DOCUMENT_LEAD_DAYS;
    }

    private VehicleDocument findCurrent(UUID vehicleId, String documentId) {
        VehicleDocument document = Uuids.parse(documentId)
                .flatMap(id -> documentRepository.findByIdAndVehicleIdAndActiveTrue(id, vehicleId))
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!document.isCurrent()) {
            throw new VehicleStateConflictException("DOCUMENT_REPLACED",
                    "Ese documento ya se renovó: se corrige o renueva el vigente");
        }
        return document;
    }

    private Vehicle findVehicle(String vehicleId) {
        return Uuids.parse(vehicleId).flatMap(vehicleRepository::findById)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    }

    private Vehicle findActiveVehicle(String vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        if (!vehicle.isActive()) {
            throw new VehicleStateConflictException("VEHICLE_INACTIVE", "El vehículo está dado de baja");
        }
        return vehicle;
    }

    private static String str(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    static VehicleDocumentDto toDto(VehicleDocument d, LocalDate today, int leadDays) {
        return new VehicleDocumentDto(
                d.getId().toString(),
                d.getVehicleId().toString(),
                d.getType().toJson(),
                d.getType().getLabel(),
                d.getName(),
                d.getDisplayName(),
                d.getNumber(),
                d.getIssuedAt() == null ? null : d.getIssuedAt().toString(),
                d.getExpiresAt() == null ? null : d.getExpiresAt().toString(),
                d.getFileUrl(),
                DocumentAlertRules.status(d.getExpiresAt(), today, leadDays),
                d.getExpiresAt() == null ? null : ChronoUnit.DAYS.between(today, d.getExpiresAt()),
                d.isCurrent(),
                d.getReplacedById() == null ? null : d.getReplacedById().toString(),
                d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
    }
}
