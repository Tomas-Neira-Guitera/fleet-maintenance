package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Email (CAM-117). Solo a quien tiene email cargado; sale con el commit y en segundo plano. */
@Component
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final EmailNotificationService email;

    public EmailNotificationSender(EmailNotificationService email) {
        this.email = email;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(List<User> recipients, NotificationContent content) {
        List<String> addresses = recipients.stream()
                .map(User::getEmail)
                .filter(address -> address != null && !address.isBlank())
                .toList();
        if (addresses.size() < recipients.size()) {
            log.info("Aviso {} sin email para {} de {} destinatarios: no tienen email cargado",
                    content.type().toJson(), recipients.size() - addresses.size(), recipients.size());
        }
        if (!addresses.isEmpty()) {
            AfterCommit.run(() -> email.send(addresses, content.title(), content.message(), content.link()));
        }
    }
}
