package org.fleetguard.service;

import org.fleetguard.dto.NotificationDto;
import org.fleetguard.entity.Notification;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.exception.NotificationNotFoundException;
import org.fleetguard.exception.NotificationValidationException;
import org.fleetguard.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-96: la campanita de cada usuario. */
class NotificationInboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationInboxService service = new NotificationInboxService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    private static Notification notification(UUID recipient) throws Exception {
        Notification n = new Notification(recipient, NotificationType.WORK_ORDER_FINALIZED, "Título", "Mensaje",
                "/ordenes-trabajo/1", NOW.minusSeconds(60));
        Field id = Notification.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(n, UUID.randomUUID());
        return n;
    }

    @Test
    void listaLosDelUsuarioSinDescartarConElLimitePorDefecto() throws Exception {
        Notification n = notification(userId);
        when(repository.findByRecipientIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(0, 30)))
                .thenReturn(List.of(n));

        List<NotificationDto> items = service.list(userId, null);

        assertEquals(1, items.size());
        assertEquals("work_order_finalized", items.get(0).type());
        assertEquals("/ordenes-trabajo/1", items.get(0).link());
        assertNull(items.get(0).readAt());
    }

    @Test
    void unLimiteFueraDeRangoEs422() {
        assertThrows(NotificationValidationException.class, () -> service.list(userId, 0));
        assertThrows(NotificationValidationException.class, () -> service.list(userId, 101));
    }

    @Test
    void cuentaLosSinLeer() {
        when(repository.countByRecipientIdAndReadAtIsNullAndDismissedAtIsNull(userId)).thenReturn(4L);
        assertEquals(4L, service.unreadCount(userId));
    }

    @Test
    void marcarLeidoEsIdempotente() throws Exception {
        Notification n = notification(userId);
        when(repository.findByIdAndRecipientId(n.getId(), userId)).thenReturn(Optional.of(n));

        service.markRead(userId, n.getId().toString());
        Instant firstRead = n.getReadAt();
        service.markRead(userId, n.getId().toString());

        assertEquals(NOW, firstRead);
        assertEquals(firstRead, n.getReadAt());
    }

    @Test
    void descartarLoSacaDelPanelYCuentaComoLeido() throws Exception {
        Notification n = notification(userId);
        when(repository.findByIdAndRecipientId(n.getId(), userId)).thenReturn(Optional.of(n));

        service.dismiss(userId, n.getId().toString());

        assertEquals(NOW, n.getDismissedAt());
        assertEquals(NOW, n.getReadAt());
    }

    @Test
    void elAvisoDeOtroUsuarioOUnIdInvalidoEs404() {
        when(repository.findByIdAndRecipientId(any(), any())).thenReturn(Optional.empty());

        NotificationNotFoundException ex = assertThrows(NotificationNotFoundException.class,
                () -> service.dismiss(userId, UUID.randomUUID().toString()));
        assertEquals("NOTIFICATION_NOT_FOUND", ex.getErrorCode());
        assertThrows(NotificationNotFoundException.class, () -> service.markRead(userId, "no-es-un-uuid"));
    }

    @Test
    void marcarTodosSoloTocaLosDelUsuario() {
        service.markAllRead(userId);
        verify(repository).markAllRead(userId, NOW);
    }

    @Test
    void unTextoDemasiadoLargoSeRecorta() {
        Notification n = new Notification(userId, NotificationType.WORK_ORDER_FINALIZED, "t".repeat(200), "m".repeat(1200),
                null, NOW);
        assertEquals(Notification.TITLE_MAX_LENGTH, n.getTitle().length());
        assertEquals(Notification.MESSAGE_MAX_LENGTH, n.getMessage().length());
    }
}
