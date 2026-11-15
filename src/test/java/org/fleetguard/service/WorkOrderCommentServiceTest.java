package org.fleetguard.service;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.CreateWorkOrderCommentRequest;
import org.fleetguard.dto.WorkOrderCommentDto;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderComment;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.event.WorkOrderCommentedEvent;
import org.fleetguard.exception.ForbiddenException;
import org.fleetguard.exception.WorkOrderConflictException;
import org.fleetguard.exception.WorkOrderNotFoundException;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.WorkOrderCommentRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-149: quién puede comentar, qué se valida y qué evento sale. */
class WorkOrderCommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final WorkOrderCommentRepository commentRepository = mock(WorkOrderCommentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final WorkOrderCommentService service = new WorkOrderCommentService(workOrderRepository, commentRepository,
            new UserNameService(userRepository), eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID technicianId = UUID.randomUUID();
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin", Role.ADMIN);
    private final AuthenticatedUser technician = new AuthenticatedUser(technicianId, "javier.romero", Role.TECNICO);
    private final AuthenticatedUser otherTechnician = new AuthenticatedUser(UUID.randomUUID(), "otro.tecnico", Role.TECNICO);
    private WorkOrder workOrder;

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        workOrder = new WorkOrder(UUID.randomUUID(), WorkOrderSourceType.MANUAL, null, null, null, "Cambio de pastillas",
                null, WorkOrderExecutionType.INTERNO, null, null, technicianId);
        setId(workOrder, UUID.randomUUID());
        when(workOrderRepository.findById(workOrder.getId())).thenReturn(Optional.of(workOrder));
        when(commentRepository.save(any())).thenAnswer(invocation -> {
            WorkOrderComment comment = invocation.getArgument(0);
            setId(comment, UUID.randomUUID());
            return comment;
        });
    }

    private WorkOrderCommentDto add(AuthenticatedUser user, String text) {
        return service.add(workOrder.getId().toString(), new CreateWorkOrderCommentRequest(text), user);
    }

    @Test
    void elTecnicoAsignadoComentaYSaleElEventoConElAutorDelToken() {
        WorkOrderCommentDto dto = add(technician, "  ¿Lo reparo o lo cambio?  ");

        assertEquals("¿Lo reparo o lo cambio?", dto.text());
        assertEquals("javier.romero", dto.author().name());
        assertEquals(technicianId.toString(), dto.author().id());
        assertEquals("TECNICO", dto.authorRole());
        assertEquals(NOW.toString(), dto.createdAt());
        ArgumentCaptor<WorkOrderCommentedEvent> event = ArgumentCaptor.forClass(WorkOrderCommentedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(new WorkOrderCommentedEvent(workOrder.getId(), technicianId, "javier.romero", Role.TECNICO,
                "¿Lo reparo o lo cambio?"), event.getValue());
    }

    @Test
    void elAdminComentaAunqueLaOtNoTengaTecnico() throws Exception {
        workOrder.setTechnicianId(null);

        assertEquals("ADMIN", add(admin, "Cambialo, hay repuesto").authorRole());
    }

    @Test
    void otroTecnicoNoPuedeVerNiComentarLaOt() {
        assertThrows(ForbiddenException.class, () -> add(otherTechnician, "Hola"));
        assertThrows(ForbiddenException.class, () -> service.list(workOrder.getId().toString(), otherTechnician));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void unaOtCerradaNoAceptaComentariosPeroSeSiguenViendo() {
        workOrder.cancel();
        when(commentRepository.findByWorkOrder_IdOrderByCreatedAtAsc(workOrder.getId())).thenReturn(List.of(
                new WorkOrderComment(workOrder, technicianId, "javier.romero", Role.TECNICO, "Esperando repuesto", NOW)));

        WorkOrderConflictException ex = assertThrows(WorkOrderConflictException.class, () -> add(admin, "¿Novedades?"));
        assertEquals("WORK_ORDER_CLOSED", ex.getErrorCode());
        assertEquals(1, service.list(workOrder.getId().toString(), admin).size());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void elTextoEsObligatorioYTieneTope() {
        assertThrows(WorkOrderValidationException.class, () -> add(admin, "   "));
        assertThrows(WorkOrderValidationException.class, () -> add(admin, null));
        assertThrows(WorkOrderValidationException.class,
                () -> add(admin, "x".repeat(TextLimits.DESCRIPTION_MAX_LENGTH + 1)));
        assertEquals(TextLimits.DESCRIPTION_MAX_LENGTH,
                add(admin, "x".repeat(TextLimits.DESCRIPTION_MAX_LENGTH)).text().length());
    }

    @Test
    void unaOtQueNoExisteEs404() {
        assertThrows(WorkOrderNotFoundException.class,
                () -> service.add(UUID.randomUUID().toString(), new CreateWorkOrderCommentRequest("Hola"), admin));
        assertThrows(WorkOrderNotFoundException.class, () -> service.list("no-es-un-uuid", admin));
    }

    @Test
    void elListadoVaDelMasViejoAlMasNuevoConElNombreActualDelAutor() throws Exception {
        User renamed = new User("javier.romero2", "hash", Role.TECNICO);
        setId(renamed, technicianId);
        when(userRepository.findAllById(any())).thenReturn(List.of(renamed));
        UUID gone = UUID.randomUUID();
        when(commentRepository.findByWorkOrder_IdOrderByCreatedAtAsc(workOrder.getId())).thenReturn(List.of(
                new WorkOrderComment(workOrder, technicianId, "javier.romero", Role.TECNICO, "Primero", NOW),
                new WorkOrderComment(workOrder, gone, "admin.viejo", Role.ADMIN, "Después", NOW.plusSeconds(60))));

        List<WorkOrderCommentDto> result = service.list(workOrder.getId().toString(), technician);

        assertEquals(List.of("Primero", "Después"), result.stream().map(WorkOrderCommentDto::text).toList());
        assertEquals("javier.romero2", result.get(0).author().name());
        // Un usuario que ya no existe queda con el nombre que tenía al comentar.
        assertEquals("admin.viejo", result.get(1).author().name());
    }
}
