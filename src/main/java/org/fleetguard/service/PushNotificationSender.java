package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Push al navegador (CAM-119). Sale recién con el commit: si el aviso se deshace, no se avisa nada. */
@Component
public class PushNotificationSender implements NotificationSender {

    private final PushNotificationService push;

    public PushNotificationSender(PushNotificationService push) {
        this.push = push;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(List<User> recipients, NotificationContent content) {
        List<UUID> userIds = recipients.stream().map(User::getId).toList();
        AfterCommit.run(() -> push.sendToUsers(userIds, content.title(), content.message(), content.link()));
    }
}
