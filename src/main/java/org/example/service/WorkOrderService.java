package org.example.service;

import org.example.dto.CreateCompletionRequest;
import org.example.dto.CreateWorkOrderExpenseRequest;
import org.example.dto.CreateWorkOrderPhotoRequest;
import org.example.dto.CreateWorkOrderRequest;
import org.example.dto.FieldValidationErrorDetail;
import org.example.dto.UpdateScheduleRequest;
import org.example.dto.UpdateWorkOrderRequest;
import org.example.dto.WorkOrderDto;
import org.example.dto.WorkOrderExpenseDto;
import org.example.dto.WorkOrderPhotoDto;
import org.example.entity.Defect;
import org.example.entity.Role;
import org.example.entity.ScheduleSourceType;
import org.example.entity.ScheduledMaintenance;
import org.example.entity.User;
import org.example.entity.Vehicle;
import org.example.entity.WorkOrder;
import org.example.entity.WorkOrderExecutionType;
import org.example.entity.WorkOrderExpense;
import org.example.entity.WorkOrderExpenseCategory;
import org.example.entity.WorkOrderPhoto;
import org.example.entity.WorkOrderSourceType;
import org.example.entity.WorkOrderStatus;
import org.example.exception.DefectNotFoundException;
import org.example.exception.ScheduleNotFoundException;
import org.example.exception.VehicleNotFoundException;
import org.example.exception.WorkOrderConflictException;
import org.example.exception.WorkOrderNotFoundException;
import org.example.exception.WorkOrderValidationException;
import org.example.mapper.WorkOrderMapper;
import org.example.repository.DefectRepository;
import org.example.repository.ScheduledMaintenanceRepository;
import org.example.repository.UserRepository;
import org.example.repository.VehicleRepository;
import org.example.repository.WorkOrderExpenseRepository;
import org.example.repository.WorkOrderPhotoRepository;
import org.example.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Órdenes de trabajo (CAM-14/CAM-15/CAM-62/CAM-63): genera y hace seguimiento de los
 * trabajos que resuelven un mantenimiento programado o un defecto, o sueltas. Al
 * finalizar, cierra el origen correspondiente -- ver claude/CAM-14-ordenes-de-trabajo.md
 * en el Project para el diseño completo.
 */
@Service
public class WorkOrderService {

    private static final FieldValidationErrorDetail TECHNICIAN_NOT_FOUND =
            new FieldValidationErrorDetail("technicianId", "No existe un técnico con ese id");
    private static final FieldValidationErrorDetail TECHNICIAN_ONLY_INTERNAL =
            new FieldValidationErrorDetail("technicianId", "Solo se puede asignar un técnico a una orden de trabajo interna");

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderExpenseRepository expenseRepository;
    private final WorkOrderPhotoRepository photoRepository;
    private final VehicleRepository vehicleRepository;
    private final DefectRepository defectRepository;
    private final ScheduledMaintenanceRepository scheduleRepository;
    private final ScheduledMaintenanceService scheduledMaintenanceService;
    private final MaintenanceCompletionService completionService;
    private final UserRepository userRepository;
    private final WorkOrderMapper mapper;

    public WorkOrderService(WorkOrderRepository workOrderRepository, WorkOrderExpenseRepository expenseRepository,
                             WorkOrderPhotoRepository photoRepository, VehicleRepository vehicleRepository,
                             DefectRepository defectRepository, ScheduledMaintenanceRepository scheduleRepository,
                             ScheduledMaintenanceService scheduledMaintenanceService,
                             MaintenanceCompletionService completionService, UserRepository userRepository,
                             WorkOrderMapper mapper) {
        this.workOrderRepository = workOrderRepository;
        this.expenseRepository = expenseRepository;
        this.photoRepository = photoRepository;
        this.vehicleRepository = vehicleRepository;
        this.defectRepository = defectRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduledMaintenanceService = scheduledMaintenanceService;
        this.completionService = completionService;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Transactional
    public WorkOrderDto create(CreateWorkOrderRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();

        WorkOrderSourceType sourceType = WorkOrderSourceType.fromJson(request.sourceType());
        if (sourceType == null) {
            details.add(new FieldValidationErrorDetail("sourceType", "Debe ser 'scheduled_maintenance', 'defect' o 'manual'"));
        }
        boolean manual = sourceType == WorkOrderSourceType.MANUAL;
        if (!manual && (request.sourceId() == null || request.sourceId().isBlank())) {
            details.add(new FieldValidationErrorDetail("sourceId", "Obligatorio para sourceType scheduled_maintenance/defect"));
        }
        if (manual && (request.vehicleId() == null || request.vehicleId().isBlank())) {
            details.add(new FieldValidationErrorDetail("vehicleId", "Obligatorio para sourceType manual"));
        }
        if (manual && (request.title() == null || request.title().isBlank())) {
            details.add(new FieldValidationErrorDetail("title", "Obligatorio para sourceType manual"));
        }

        WorkOrderExecutionType executionType = WorkOrderExecutionType.fromJson(request.executionType());
        if (executionType == null) {
            details.add(new FieldValidationErrorDetail("executionType", "Debe ser 'interno' o 'externo'"));
        }
        if (executionType == WorkOrderExecutionType.EXTERNO
                && (request.externalProvider() == null || request.externalProvider().isBlank())) {
            details.add(new FieldValidationErrorDetail("externalProvider", "Obligatorio si executionType es 'externo'"));
        }
        UUID technicianId = null;
        if (request.technicianId() != null && !request.technicianId().isBlank()) {
            if (executionType == WorkOrderExecutionType.EXTERNO) {
                details.add(TECHNICIAN_ONLY_INTERNAL);
            } else {
                technicianId = resolveTechnicianId(request.technicianId());
                if (technicianId == null) {
                    details.add(TECHNICIAN_NOT_FOUND);
                }
            }
        }
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Datos inválidos para crear la orden de trabajo", details);
        }

        UUID vehicleId;
        UUID scheduledMaintenanceId = null;
        UUID defectId = null;
        UUID assignmentId = null;
        String title;

        if (sourceType == WorkOrderSourceType.SCHEDULED_MAINTENANCE) {
            ScheduledMaintenance schedule = scheduleRepository.findById(UUID.fromString(request.sourceId()))
                    .orElseThrow(() -> new ScheduleNotFoundException(request.sourceId()));
            vehicleId = schedule.getVehicleId();
            scheduledMaintenanceId = schedule.getId();
            defectId = schedule.getDefectId();
            assignmentId = schedule.getAssignmentId();
            title = schedule.getTitle();
        } else if (sourceType == WorkOrderSourceType.DEFECT) {
            Defect defect = defectRepository.findById(UUID.fromString(request.sourceId()))
                    .orElseThrow(() -> new DefectNotFoundException(request.sourceId()));
            if (!"open".equals(defect.getStatus())) {
                throw new WorkOrderConflictException("DEFECT_RESOLVED",
                        "No se puede abrir una orden de trabajo sobre un defecto ya resuelto");
            }
            vehicleId = defect.getInspectionAnswer().getInspection().getVehicleId();
            defectId = defect.getId();
            title = defect.getDescription();
        } else {
            Vehicle vehicle = vehicleRepository.findById(UUID.fromString(request.vehicleId()))
                    .orElseThrow(() -> new VehicleNotFoundException(request.vehicleId()));
            vehicleId = vehicle.getId();
            title = request.title();
        }

        WorkOrder workOrder = new WorkOrder(vehicleId, sourceType, scheduledMaintenanceId, defectId, assignmentId,
                title, request.description(), executionType, request.externalProvider(), request.assignee(), technicianId);
        workOrderRepository.save(workOrder);

        return toDto(workOrder);
    }

    public WorkOrderDto get(String id) {
        return toDto(findWorkOrder(id));
    }

    public List<WorkOrderDto> list(String vehicleIdParam, String statusParam, String executionTypeParam,
                                   String technicianIdParam) {
        List<WorkOrder> workOrders = vehicleIdParam != null && !vehicleIdParam.isBlank()
                ? workOrderRepository.findByVehicleIdOrderByCreatedAtDesc(UUID.fromString(vehicleIdParam))
                : workOrderRepository.findAllByOrderByCreatedAtDesc();

        if (statusParam != null) {
            WorkOrderStatus status = WorkOrderStatus.fromJson(statusParam);
            workOrders = workOrders.stream().filter(w -> w.getStatus() == status).toList();
        }
        if (executionTypeParam != null) {
            WorkOrderExecutionType executionType = WorkOrderExecutionType.fromJson(executionTypeParam);
            workOrders = workOrders.stream().filter(w -> w.getExecutionType() == executionType).toList();
        }
        if (technicianIdParam != null && !technicianIdParam.isBlank()) {
            // Comparación por string: un id malformado o inexistente devuelve lista vacía, no 500.
            workOrders = workOrders.stream()
                    .filter(w -> w.getTechnicianId() != null
                            && w.getTechnicianId().toString().equalsIgnoreCase(technicianIdParam.trim()))
                    .toList();
        }

        return workOrders.stream().map(this::toDto).toList();
    }

    @Transactional
    public WorkOrderDto update(String id, UpdateWorkOrderRequest request) {
        WorkOrder workOrder = findWorkOrder(id);

        if (request.assignee() != null) {
            workOrder.setAssignee(request.assignee());
        }
        if (request.description() != null) {
            workOrder.setDescription(request.description());
        }
        if (request.executionType() != null) {
            WorkOrderExecutionType executionType = WorkOrderExecutionType.fromJson(request.executionType());
            if (executionType == null) {
                throw new WorkOrderValidationException("Tipo de ejecución inválido",
                        List.of(new FieldValidationErrorDetail("executionType", "Debe ser 'interno' o 'externo'")));
            }
            if (executionType == WorkOrderExecutionType.INTERNO
                    && workOrder.getExecutionType() == WorkOrderExecutionType.EXTERNO) {
                // CAM-60: en una OT interna el responsable es technicianId; el contacto y el
                // proveedor externos dejan de aplicar y no deben quedar como "responsable".
                workOrder.setAssignee(null);
                workOrder.setExternalProvider(null);
            }
            workOrder.setExecutionType(executionType);
        }
        if (request.externalProvider() != null) {
            workOrder.setExternalProvider(request.externalProvider());
        }
        if (workOrder.getExecutionType() == WorkOrderExecutionType.EXTERNO
                && (workOrder.getExternalProvider() == null || workOrder.getExternalProvider().isBlank())) {
            throw new WorkOrderValidationException("Datos inválidos",
                    List.of(new FieldValidationErrorDetail("externalProvider", "Obligatorio si executionType es 'externo'")));
        }
        applyTechnicianChange(workOrder, request.technicianId());

        if (request.status() != null) {
            applyStatusChange(workOrder, request);
        }

        workOrderRepository.save(workOrder);
        return toDto(workOrder);
    }

    /**
     * CAM-60. technicianId ausente (null) no toca nada; "" desasigna; un id asigna. El técnico
     * solo tiene sentido en OTs internas: si la OT queda externa, se desasigna sola.
     */
    private void applyTechnicianChange(WorkOrder workOrder, String technicianIdParam) {
        boolean external = workOrder.getExecutionType() == WorkOrderExecutionType.EXTERNO;
        if (technicianIdParam != null && !technicianIdParam.isBlank()) {
            if (external) {
                throw new WorkOrderValidationException("Datos inválidos", List.of(TECHNICIAN_ONLY_INTERNAL));
            }
            UUID technicianId = resolveTechnicianId(technicianIdParam);
            if (technicianId == null) {
                throw new WorkOrderValidationException("Datos inválidos", List.of(TECHNICIAN_NOT_FOUND));
            }
            workOrder.setTechnicianId(technicianId);
        } else if ((technicianIdParam != null || external) && workOrder.getTechnicianId() != null) {
            workOrder.setTechnicianId(null);
        }
    }

    /** Devuelve el id si es un usuario con rol TECNICO; null si no existe, no es técnico o está malformado. */
    private UUID resolveTechnicianId(String technicianIdParam) {
        UUID id;
        try {
            id = UUID.fromString(technicianIdParam);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return userRepository.findById(id).filter(u -> u.getRole() == Role.TECNICO).map(User::getId).orElse(null);
    }

    private void applyStatusChange(WorkOrder workOrder, UpdateWorkOrderRequest request) {
        WorkOrderStatus newStatus = WorkOrderStatus.fromJson(request.status());
        if (newStatus == null) {
            throw new WorkOrderValidationException("Estado inválido",
                    List.of(new FieldValidationErrorDetail("status", "Debe ser 'en_proceso', 'finalizada' o 'cancelada'")));
        }

        WorkOrderStatus current = workOrder.getStatus();
        boolean validTransition = switch (newStatus) {
            case EN_PROCESO -> current == WorkOrderStatus.ASIGNADA;
            case FINALIZADA -> current == WorkOrderStatus.EN_PROCESO;
            case CANCELADA -> current == WorkOrderStatus.ASIGNADA || current == WorkOrderStatus.EN_PROCESO;
            case ASIGNADA -> false;
        };
        if (!validTransition) {
            throw new WorkOrderConflictException("INVALID_STATUS_TRANSITION",
                    "No se puede pasar de '" + current.toJson() + "' a '" + newStatus.toJson() + "'");
        }

        if (newStatus == WorkOrderStatus.EN_PROCESO) {
            workOrder.start();
        } else if (newStatus == WorkOrderStatus.CANCELADA) {
            workOrder.cancel();
        } else {
            finalizeWorkOrder(workOrder, request);
        }
    }

    /**
     * Cierra el loop del origen -- ver claude/CAM-14-ordenes-de-trabajo.md sección 2. Si hay
     * assignmentId, se delega en MaintenanceCompletionService.create (ya existente), que
     * además cierra sola la scheduled_maintenance de origen assignment. Si la scheduled_
     * maintenance es de origen defect/manual, se cierra acá con el update ya existente.
     */
    private void finalizeWorkOrder(WorkOrder workOrder, UpdateWorkOrderRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.closingDescription() == null || request.closingDescription().isBlank()) {
            details.add(new FieldValidationErrorDetail("closingDescription", "Obligatoria para finalizar la orden de trabajo"));
        }
        long photoCount = photoRepository.countByWorkOrder_Id(workOrder.getId());
        if (photoCount == 0) {
            details.add(new FieldValidationErrorDetail("photos", "Hace falta al menos una foto para finalizar la orden de trabajo"));
        }
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Datos inválidos para finalizar la orden de trabajo", details);
        }

        workOrder.finalizeOrder(request.closingDescription());

        if (workOrder.getAssignmentId() != null) {
            CreateCompletionRequest completionRequest = new CreateCompletionRequest(
                    LocalDate.now().toString(), request.completedKm(), workOrder.getId().toString(),
                    "Registrado automáticamente al finalizar la orden de trabajo");
            completionService.create(workOrder.getVehicleId().toString(), workOrder.getAssignmentId().toString(), completionRequest);
        } else if (workOrder.getScheduledMaintenanceId() != null) {
            scheduleRepository.findById(workOrder.getScheduledMaintenanceId()).ifPresent(schedule -> {
                if (schedule.getSourceType() != ScheduleSourceType.ASSIGNMENT) {
                    scheduledMaintenanceService.update(schedule.getId().toString(), new UpdateScheduleRequest(null, "done"));
                }
            });
        }

        if (workOrder.getDefectId() != null) {
            defectRepository.findById(workOrder.getDefectId()).ifPresent(Defect::resolve);
        }
    }

    @Transactional
    public WorkOrderExpenseDto addExpense(String id, CreateWorkOrderExpenseRequest request) {
        WorkOrder workOrder = findWorkOrder(id);
        requireOpen(workOrder, "WORK_ORDER_FINALIZED", "No se pueden cargar gastos sobre una orden de trabajo finalizada o cancelada");

        WorkOrderExpenseCategory category = WorkOrderExpenseCategory.fromJson(request.category());
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (category == null) {
            details.add(new FieldValidationErrorDetail("category", "Debe ser 'repuesto', 'mano_de_obra' u 'otro'"));
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            details.add(new FieldValidationErrorDetail("amount", "Debe ser mayor a 0"));
        }
        if (request.description() == null || request.description().isBlank()) {
            details.add(new FieldValidationErrorDetail("description", "Obligatoria"));
        }
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Datos inválidos para el gasto", details);
        }

        WorkOrderExpense expense = new WorkOrderExpense(workOrder, category, request.description(), request.amount());
        expenseRepository.save(expense);
        return mapper.toExpenseDto(expense);
    }

    @Transactional
    public void deleteExpense(String id, String expenseId) {
        WorkOrder workOrder = findWorkOrder(id);
        requireOpen(workOrder, "WORK_ORDER_FINALIZED", "No se pueden borrar gastos de una orden de trabajo finalizada o cancelada");
        WorkOrderExpense expense = expenseRepository.findById(UUID.fromString(expenseId))
                .filter(e -> e.getWorkOrder().getId().equals(workOrder.getId()))
                .orElseThrow(() -> new WorkOrderNotFoundException(expenseId));
        expenseRepository.delete(expense);
    }

    @Transactional
    public WorkOrderPhotoDto addPhoto(String id, CreateWorkOrderPhotoRequest request) {
        WorkOrder workOrder = findWorkOrder(id);
        requireOpen(workOrder, "WORK_ORDER_CLOSED", "No se pueden agregar fotos a una orden de trabajo finalizada o cancelada");

        if (request.photoUrl() == null || request.photoUrl().isBlank()) {
            throw new WorkOrderValidationException("Datos inválidos",
                    List.of(new FieldValidationErrorDetail("photoUrl", "Obligatoria")));
        }

        WorkOrderPhoto photo = new WorkOrderPhoto(workOrder, request.photoUrl());
        photoRepository.save(photo);
        return mapper.toPhotoDto(photo);
    }

    @Transactional
    public void deletePhoto(String id, String photoId) {
        WorkOrder workOrder = findWorkOrder(id);
        requireOpen(workOrder, "WORK_ORDER_CLOSED", "No se pueden borrar fotos de una orden de trabajo finalizada o cancelada");
        WorkOrderPhoto photo = photoRepository.findById(UUID.fromString(photoId))
                .filter(p -> p.getWorkOrder().getId().equals(workOrder.getId()))
                .orElseThrow(() -> new WorkOrderNotFoundException(photoId));
        photoRepository.delete(photo);
    }

    private void requireOpen(WorkOrder workOrder, String errorCode, String message) {
        if (!workOrder.isOpen()) {
            throw new WorkOrderConflictException(errorCode, message);
        }
    }

    private WorkOrder findWorkOrder(String id) {
        return workOrderRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new WorkOrderNotFoundException(id));
    }

    private WorkOrderDto toDto(WorkOrder workOrder) {
        String plate = vehicleRepository.findById(workOrder.getVehicleId()).map(Vehicle::getPlate).orElse(null);
        List<WorkOrderExpense> expenses = expenseRepository.findByWorkOrder_IdOrderByCreatedAtAsc(workOrder.getId());
        List<WorkOrderPhoto> photos = photoRepository.findByWorkOrder_IdOrderByCreatedAtAsc(workOrder.getId());
        String technicianUsername = workOrder.getTechnicianId() == null ? null
                : userRepository.findById(workOrder.getTechnicianId()).map(User::getUsername).orElse(null);
        return mapper.toDto(workOrder, plate, technicianUsername, expenses, photos);
    }
}
