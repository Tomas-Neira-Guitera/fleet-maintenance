package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-119 -- cada aviso va a la bandeja (CAM-96) y además sale como push, después del commit. */
class PushingNotificationServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<NotificationService> provider = mock(ObjectProvider.class);
    private final NotificationService inbox = mock(NotificationService.class);
    private final PushNotificationService push = mock(PushNotificationService.class);
    private final PushingNotificationService service = new PushingNotificationService(provider, push);

    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void withInbox() {
        // El provider devuelve todas las NotificationService, esta incluida: no tiene que llamarse a sí misma.
        when(provider.orderedStream()).thenAnswer(inv -> Stream.of(service, inbox));
    }

    @Test
    void sinTransaccionDejaElAvisoEnLaBandejaYMandaLaPushEnElMomento() {
        withInbox();

        service.notify(userId, NotificationType.WORK_ORDER_ASSIGNED, "Título", "Mensaje", "/");
        service.notifyAllAdmins(NotificationType.WORK_ORDER_ASSIGNED, "Título", "Mensaje", "/vehiculos/1");

        verify(inbox).notify(userId, NotificationType.WORK_ORDER_ASSIGNED, "Título", "Mensaje", "/");
        verify(inbox).notifyAllAdmins(NotificationType.WORK_ORDER_ASSIGNED, "Título", "Mensaje", "/vehiculos/1");
        verify(push).sendToUser(userId, "Título", "Mensaje", "/");
        verify(push).sendToAdmins("Título", "Mensaje", "/vehiculos/1");
    }

    @Test
    void dentroDeUnaTransaccionLaPushEsperaAlCommit() {
        withInbox();
        TransactionSynchronizationManager.initSynchronization();

        service.notify(userId, NotificationType.TRIP_OPEN_REMINDER, "Título", "Mensaje", "/");
        service.notifyAllAdmins(NotificationType.TRIP_OPEN_REMINDER, "Título", "Mensaje", "/");

        verify(inbox).notify(userId, NotificationType.TRIP_OPEN_REMINDER, "Título", "Mensaje", "/");
        verify(push, never()).sendToUser(userId, "Título", "Mensaje", "/");

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(push).sendToUser(userId, "Título", "Mensaje", "/");
        verify(push).sendToAdmins("Título", "Mensaje", "/");
    }

    @Test
    void siLaTransaccionSeDeshaceNoSaleLaPush() {
        withInbox();
        TransactionSynchronizationManager.initSynchronization();

        service.notify(userId, NotificationType.TRIP_OPEN_REMINDER, "Título", "Mensaje", "/");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(push, never()).sendToUser(userId, "Título", "Mensaje", "/");
    }
}
