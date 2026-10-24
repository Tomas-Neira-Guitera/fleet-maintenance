package org.fleetguard.service;

import org.fleetguard.entity.Notification;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.User;
import org.fleetguard.repository.NotificationRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** La campanita (CAM-96): un aviso guardado por destinatario. */
@Component
public class InboxNotificationSender implements NotificationSender {

    private final NotificationRepository repository;
    private final Clock clock;

    public InboxNotificationSender(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void send(List<User> recipients, NotificationContent content) {
        Instant now = clock.instant();
        repository.saveAll(recipients.stream()
                .map(user -> new Notification(user.getId(), content.type(), content.title(), content.message(),
                        content.link(), now))
                .toList());
    }
}
