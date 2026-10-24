package org.fleetguard.service;

import org.fleetguard.entity.Notification;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** CAM-117: cada canal entrega a sus destinatarios. */
class NotificationSendersTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private final NotificationContent content = new NotificationContent(NotificationType.WORK_ORDER_FINALIZED, "T", "M", "/x");

    private static User user(String email) throws Exception {
        User user = new User("u" + UUID.randomUUID(), "hash", Role.ADMIN);
        user.setEmail(email);
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, UUID.randomUUID());
        return user;
    }

    @Test
    @SuppressWarnings("unchecked")
    void laCampanitaGuardaUnAvisoPorDestinatario() throws Exception {
        NotificationRepository repository = mock(NotificationRepository.class);
        InboxNotificationSender sender = new InboxNotificationSender(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        User a = user(null);
        User b = user(null);

        sender.send(List.of(a, b), content);

        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertEquals(List.of(a.getId(), b.getId()), saved.getValue().stream().map(Notification::getRecipientId).toList());
        assertEquals(NOW, saved.getValue().get(0).getCreatedAt());
        assertEquals(NotificationChannel.IN_APP, sender.channel());
    }

    @Test
    void laPushSaleSinTransaccionEnElMomento() throws Exception {
        PushNotificationService push = mock(PushNotificationService.class);
        PushNotificationSender sender = new PushNotificationSender(push);
        User a = user(null);

        sender.send(List.of(a), content);

        verify(push).sendToUsers(List.of(a.getId()), "T", "M", "/x");
        assertEquals(NotificationChannel.PUSH, sender.channel());
    }

    @Test
    void elEmailSoloVaAQuienTieneEmail() throws Exception {
        EmailNotificationService email = mock(EmailNotificationService.class);
        EmailNotificationSender sender = new EmailNotificationSender(email);

        sender.send(List.of(user("a@x.com"), user(null), user("  ")), content);

        verify(email).send(List.of("a@x.com"), "T", "M", "/x");
        assertEquals(NotificationChannel.EMAIL, sender.channel());
    }

    @Test
    void sinNingunEmailNoLlamaAlServicio() throws Exception {
        EmailNotificationService email = mock(EmailNotificationService.class);

        new EmailNotificationSender(email).send(List.of(user(null)), content);

        verify(email, never()).send(any(), any(), any(), any());
    }
}
