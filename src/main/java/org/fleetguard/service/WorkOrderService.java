package org.fleetguard.service;

import org.fleetguard.dto.CreateCompletionRequest;
import org.fleetguard.dto.CreateWorkOrderExpenseRequest;
import org.fleetguard.dto.CreateWorkOrderPhotoRequest;
import org.fleetguard.dto.CreateWorkOrderRequest;
import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.UpdateScheduleRequest;
import org.fleetguard.dto.UpdateWorkOrderRequest;
import org.fleetguard.dto.WorkOrderDto;
import org.fleetguard.dto.WorkOrderExpenseDto;
import org.fleetguard.dto.WorkOrderPhotoDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.ScheduleSourceType;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.User;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderExpense;
import org.fleetguard.entity.WorkOrderExpenseCategory;
import org.fleetguard.entity.WorkOrderPhoto;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.exception.DefectNotFoundException;
import org.fleetguard.exception.ScheduleNotFoundException;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.exception.WorkOrderConflictException;
import org.fleetguard.exception.WorkOrderNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.mapper.WorkOrderMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.VehicleRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.fleetguard.repository.WorkOrderPhotoRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            new FieldValidationErrorDetail("technicianId", "No existe un técnico activo con ese id");
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
    private final DefectMapper defectMapper;

    public WorkOrderService(WorkOrderRepository workOrderRepository, WorkOrderExpenseRepository expenseRepository,
                             WorkOrderPhotoRepository photoRepository, VehicleRepository vehicleRepository,
                             DefectRepository defectRepository, ScheduledMaintenanceRepository scheduleRepository,
                             ScheduledMaintenanceService scheduledMaintenanceService,
                             MaintenanceCompletionService completionService, UserRepository userRepository,
                             WorkOrderMapper mapper, DefectMapper defectMapper) {
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
        this.defectMapper = defectMapper;
    }

    /** created=false si ya había una OT abierta para el mismo origen y se devolvió esa. */
    public record CreateResult(WorkOrderDto dto, boolean created) {
    }

    private static final List<WorkOrderStatus> OPEN_STATUSES = List.of(WorkOrderStatus.ASIGNADA, WorkOrderStatus.EN_PROCESO);

    @Transactional
    public CreateResult create(CreateWorkOrderRequest request) {
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
        } else if (manual && TextLimits.exceedsTitle(request.title())) {
            details.add(new FieldValidationErrorDetail("title", TextLimits.titleTooLongMessage()));
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
            ScheduledMaintenance schedule = Uuids.parse(request.sourceId()).flatMap(scheduleRepository::findById)
                    .orElseThrow(() -> new ScheduleNotFoundException(request.sourceId()));
            vehicleId = schedule.getVehicleId();
            scheduledMaintenanceId = schedule.getId();
            defectId = schedule.getDefectId();
            assignmentId = schedule.getAssignmentId();
            title = schedule.getTitle();
        } else if (sourceType == WorkOrderSourceType.DEFECT) {
            Defect defect = Uuids.parse(request.sourceId()).flatMap(defectRepository::findById)
                    .orElseThrow(() -> new DefectNotFoundException(request.sourceId()));
            if (!"open".equals(defect.getStatus())) {
                throw new WorkOrderConflictException("DEFECT_RESOLVED",
                        "No se puede abrir una orden de trabajo sobre un defecto ya resuelto");
            }
            vehicleId = defect.getInspectionAnswer().getInspection().getVehicleId();
            defectId = defect.getId();
            title = defect.getDescription();
        } else {
            Vehicle vehicle = Uuids.parse(request.vehicleId()).flatMap(vehicleRepository::findById)
                    .orElseThrow(() -> new VehicleNotFoundException(request.vehicleId()));
            vehicleId = vehicle.getId();
            title = request.title().trim();
        }

        // Una sola OT abierta por origen: replanificar desde el calendario (o volver a planificar un
        // defecto) no debe duplicar el trabajo del técnico. Se devuelve la existente sin cambiar sus
        // datos; solo se la vincula a la programación vigente, que es la que tiene que cerrar al finalizar
        // (si la anterior se canceló y se replanificó, el calendario creó una programación nueva).
        Optional<WorkOrder> open = findOpenForSource(defectId, assignmentId, scheduledMaintenanceId);
        if (open.isPresent()) {
            WorkOrder existing = open.get();
            if (scheduledMaintenanceId != null && !scheduledMaintenanceId.equals(existing.getScheduledMaintenanceId())) {
                existing.setScheduledMaintenanceId(scheduledMaintenanceId);
                workOrderRepository.save(existing);
            }
            return new CreateResult(toDto(existing), false);
        }

        WorkOrder workOrder = new WorkOrder(vehicleId, sourceType, scheduledMaintenanceId, defectId, assignmentId,
                title, request.description(), executionType, request.externalProvider(), request.assignee(), technicianId);
        workOrderRepository.save(workOrder);

        return new CreateResult(toDto(workOrder), true);
    }

    /**
     * Se busca por el origen de fondo, no solo por la programación: el defecto (cubre la OT directa y
     * la que pasa por el calendario) o la asignación del plan (sobrevive a cancelar y replanificar,
     * que crea otra programación). Si no hay ninguno de los dos, la programación manual.
     */
    private Optional<WorkOrder> findOpenForSource(UUID defectId, UUID assignmentId, UUID scheduledMaintenanceId) {
        if (defectId != null) {
            return workOrderRepository.findFirstByDefectIdAndStatusInOrderByCreatedAtAsc(defectId, OPEN_STATUSES);
        }
        if (assignmentId != null) {
            return workOrderRepository.findFirstByAssignmentIdAndStatusInOrderByCreatedAtAsc(assignmentId, OPEN_STATUSES);
        }
        if (scheduledMaintenanceId != null) {
            return workOrderRepository.findFirstByScheduledMaintenanceIdAndStatusInOrderByCreatedAtAsc(
                    scheduledMaintenanceId, OPEN_STATUSES);
        }
        return Optional.empty();
    }

    public WorkOrderDto get(String id) {
        return toDto(findWorkOrder(id));
    }

    public List<WorkOrderDto> list(String vehicleIdParam, String statusParam, String executionTypeParam,
                                   String technicianIdParam) {
        List<WorkOrder> workOrders = vehicleIdParam != null && !vehicleIdParam.isBlank()
                ? Uuids.parse(vehicleIdParam).map(workOrderRepository::findByVehicleIdOrderByCreatedAtDesc).orElse(List.of())
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
        // CAM-75: una OT finalizada o cancelada queda como registro histórico. Va antes que todo,
        // así un intento de cambiarle el estado también responde WORK_ORDER_CLOSED.
        requireOpen(workOrder, "WORK_ORDER_CLOSED", "No se puede editar una orden de trabajo finalizada o cancelada");

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
            // El formulario de edición reenvía el técnico que ya está a cargo: si después lo
            // desactivaron (CAM-23), editar otro campo de la OT no tiene que rechazarse por eso.
            if (workOrder.getTechnicianId() != null
                    && workOrder.getTechnicianId().toString().equalsIgnoreCase(technicianIdParam.trim())) {
                return;
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

    /**
     * Devuelve el id si es un usuario activo con rol TECNICO; null si no existe, no es técnico,
     * está desactivado (CAM-23) o el id está malformado.
     */
    private UUID resolveTechnicianId(String technicianIdParam) {
        UUID id;
        try {
            id = UUID.fromString(technicianIdParam);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.TECNICO && u.isActive())
                .map(User::getId)
                .orElse(null);
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
        Long completedKm = validateCompletedKm(workOrder, request.completedKm(), details);
        if (!details.isEmpty()) {
            throw new WorkOrderValidationException("Datos inválidos para finalizar la orden de trabajo", details);
        }

        workOrder.finalizeOrder(request.closingDescription());

        if (workOrder.getAssignmentId() != null) {
            CreateCompletionRequest completionRequest = new CreateCompletionRequest(
                    LocalDate.now().toString(), completedKm, workOrder.getId().toString(),
                    "Registrado automáticamente al finalizar la orden de trabajo");
            completionService.create(workOrder.getVehicleId().toString(), workOrder.getAssignmentId().toString(), completionRequest);
        } else if (workOrder.getScheduledMaintenanceId() != null) {
            scheduleRepository.findById(workOrder.getScheduledMaintenanceId()).ifPresent(schedule -> {
                if (schedule.getSourceType() != ScheduleSourceType.ASSIGNMENT) {
                    scheduledMaintenanceService.update(schedule.getId().toString(), new UpdateScheduleRequest(null, "done", null));
                }
            });
        }

        if (workOrder.getDefectId() != null) {
            defectRepository.findById(workOrder.getDefectId()).ifPresent(Defect::resolve);
        }
    }

    /**
     * CAM-74: el km al finalizar es un entero, no negativo y no menor al que ya tiene cargado el
     * vehículo (el odómetro no retrocede, mismo criterio que ODOMETER_REGRESSION). Ausente es
     * válido acá; si el plan lo exige, lo reclama MaintenanceCompletionService.
     */
    private Long validateCompletedKm(WorkOrder workOrder, BigDecimal km, List<FieldValidationErrorDetail> details) {
        if (km == null) {
            return null;
        }
        if (km.signum() < 0) {
            details.add(new FieldValidationErrorDetail("completedKm", "El kilometraje no puede ser negativo"));
            return null;
        }
        // stripTrailingZeros y no remainder(ONE): con un exponente enorme ("1e100000", 8 caracteres
        // que Jackson acepta) remainder arma un número de 10^N dígitos y traba el hilo varios segundos.
        if (km.stripTrailingZeros().scale() > 0) {
            details.add(new FieldValidationErrorDetail("completedKm",
                    "El kilometraje tiene que ser un número entero, sin decimales"));
            return null;
        }
        long value;
        try {
            value = km.longValueExact();
        } catch (ArithmeticException e) {
            details.add(new FieldValidationErrorDetail("completedKm", "El kilometraje está fuera de rango"));
            return null;
        }
        long current = vehicleRepository.findById(workOrder.getVehicleId()).map(Vehicle::getOdometerKm).orElse(0L);
        if (value < current) {
            details.add(new FieldValidationErrorDetail("completedKm",
                    "El kilometraje no puede ser menor al que ya tiene cargado el vehículo (" + current + " km)"));
            return null;
        }
        return value;
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
        WorkOrderExpense expense = Uuids.parse(expenseId).flatMap(expenseRepository::findById)
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
        WorkOrderPhoto photo = Uuids.parse(photoId).flatMap(photoRepository::findById)
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
        return Uuids.parse(id).flatMap(workOrderRepository::findById)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));
    }

    private WorkOrderDto toDto(WorkOrder workOrder) {
        String plate = vehicleRepository.findById(workOrder.getVehicleId()).map(Vehicle::getPlate).orElse(null);
        List<WorkOrderExpense> expenses = expenseRepository.findByWorkOrder_IdOrderByCreatedAtAsc(workOrder.getId());
        List<WorkOrderPhoto> photos = photoRepository.findByWorkOrder_IdOrderByCreatedAtAsc(workOrder.getId());
        String technicianUsername = workOrder.getTechnicianId() == null ? null
                : userRepository.findById(workOrder.getTechnicianId()).map(User::getUsername).orElse(null);
        DefectDto defect = workOrder.getDefectId() == null ? null
                : defectRepository.findByIdWithInspection(workOrder.getDefectId())
                        .map(d -> defectMapper.toDto(d, workOrder.getVehicleId().toString(), plate))
                        .orElse(null);
        return mapper.toDto(workOrder, plate, technicianUsername, defect, expenses, photos);
    }
}
