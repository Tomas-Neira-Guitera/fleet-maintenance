package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * La NotificationService que reciben los avisos (@Primary, CAM-119): deja el aviso en la bandeja
 * (la otra implementación de NotificationService: hoy LoggingNotificationService, después la que lo
 * guarda en la base, CAM-96) y además lo manda como push a los navegadores del usuario.
 *
 * Así la push no depende de CAM-96: quien implemente la bandeja no tiene que acordarse de llamarla,
 * le alcanza con reemplazar LoggingNotificationService. La push sale recién cuando se confirma la
 * transacción del aviso: si se deshace, no se avisa nada que después no esté en la campanita.
 */
@Primary
@Service
public class PushingNotificationService implements NotificationService {

    private final ObjectProvider<NotificationService> notificationServices;
    private final PushNotificationService push;

    public PushingNotificationService(ObjectProvider<NotificationService> notificationServices,
                                      PushNotificationService push) {
        this.notificationServices = notificationServices;
        this.push = push;
    }

    @Override
    public void notify(UUID recipientId, NotificationType type, String title, String message, String link) {
        inboxes().forEach(inbox -> inbox.notify(recipientId, type, title, message, link));
        afterCommit(() -> push.sendToUser(recipientId, title, message, link));
    }

    @Override
    public void notifyAllAdmins(NotificationType type, String title, String message, String link) {
        inboxes().forEach(inbox -> inbox.notifyAllAdmins(type, title, message, link));
        afterCommit(() -> push.sendToAdmins(title, message, link));
    }

    /** Todas las NotificationService menos esta: la bandeja. Se busca en cada aviso, no al crearla. */
    private Stream<NotificationService> inboxes() {
        return notificationServices.orderedStream().filter(service -> service != this);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
