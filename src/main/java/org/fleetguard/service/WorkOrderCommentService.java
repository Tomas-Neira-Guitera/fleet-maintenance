package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.CreateWorkOrderCommentRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.UserRefDto;
import org.fleetguard.dto.WorkOrderCommentDto;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderComment;
import org.fleetguard.event.WorkOrderCommentedEvent;
import org.fleetguard.exception.ForbiddenException;
import org.fleetguard.exception.WorkOrderConflictException;
import org.fleetguard.exception.WorkOrderNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.WorkOrderCommentRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * CAM-149: comentarios en una orden de trabajo, entre el admin y el técnico. Ver los dos endpoints en
 * openapi.yaml (/work-orders/{id}/comments).
 * <ul>
 *   <li>Pueden el admin y el técnico asignado. Hasta CAM-69 el resto de /work-orders no mira quién es el
 *       técnico, así que acá se chequea a mano: otro técnico recibe 403.</li>
 *   <li>No se editan ni se borran. Una OT finalizada o cancelada no acepta comentarios nuevos (CAM-75), y
 *       los anteriores se siguen viendo.</li>
 *   <li>El aviso sale después del commit (WorkOrderNotificationListener).</li>
 * </ul>
 */
@Service
public class WorkOrderCommentService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderCommentRepository commentRepository;
    private final UserNameService userNameService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public WorkOrderCommentService(WorkOrderRepository workOrderRepository, WorkOrderCommentRepository commentRepository,
                                   UserNameService userNameService, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.workOrderRepository = workOrderRepository;
        this.commentRepository = commentRepository;
        this.userNameService = userNameService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkOrderCommentDto> list(String workOrderId, AuthenticatedUser user) {
        WorkOrder workOrder = findAllowed(workOrderId, user);
        List<WorkOrderComment> comments = commentRepository.findByWorkOrder_IdOrderByCreatedAtAsc(workOrder.getId());
        Map<String, String> names = userNameService.currentNames(
                comments.stream().map(c -> c.getAuthorId().toString()).toList());
        return comments.stream().map(c -> toDto(c, names)).toList();
    }

    @Transactional
    public WorkOrderCommentDto add(String workOrderId, CreateWorkOrderCommentRequest request, AuthenticatedUser user) {
        WorkOrder workOrder = findAllowed(workOrderId, user);
        if (!workOrder.isOpen()) {
            throw new WorkOrderConflictException("WORK_ORDER_CLOSED",
                    "No se pueden agregar comentarios a una orden de trabajo finalizada o cancelada");
        }
        String text = request == null || request.text() == null ? "" : request.text().strip();
        if (text.isEmpty()) {
            throw invalid("Obligatorio");
        }
        if (text.length() > TextLimits.DESCRIPTION_MAX_LENGTH) {
            throw invalid("No puede superar los " + TextLimits.DESCRIPTION_MAX_LENGTH + " caracteres.");
        }

        WorkOrderComment comment = commentRepository.save(new WorkOrderComment(workOrder, user.id(), user.username(),
                user.role(), text, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        eventPublisher.publishEvent(new WorkOrderCommentedEvent(workOrder.getId(), user.id(), user.username(),
                user.role(), text));
        return toDto(comment, Map.of(user.id().toString(), user.username()));
    }

    /** 404 si no existe; 403 si es un técnico que no la tiene asignada. */
    private WorkOrder findAllowed(String workOrderId, AuthenticatedUser user) {
        WorkOrder workOrder = Uuids.parse(workOrderId).flatMap(workOrderRepository::findById)
                .orElseThrow(() -> new WorkOrderNotFoundException(workOrderId));
        if (user.role() == Role.TECNICO && !user.id().equals(workOrder.getTechnicianId())) {
            throw new ForbiddenException();
        }
        return workOrder;
    }

    private static WorkOrderValidationException invalid(String message) {
        return new WorkOrderValidationException("Datos inválidos", List.of(new FieldValidationErrorDetail("text", message)));
    }

    private static WorkOrderCommentDto toDto(WorkOrderComment comment, Map<String, String> currentNames) {
        String authorId = comment.getAuthorId().toString();
        return new WorkOrderCommentDto(comment.getId() == null ? null : comment.getId().toString(),
                new UserRefDto(authorId, currentNames.getOrDefault(authorId, comment.getAuthorName())),
                comment.getAuthorRole().name(), comment.getText(), comment.getCreatedAt().toString());
    }
}
