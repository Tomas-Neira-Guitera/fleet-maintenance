package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.User;
import org.fleetguard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Decide a quién y por qué canales sale cada aviso según la configuración (CAM-117) y lo reparte. */
@Service
public class DefaultNotificationService implements NotificationService {

    private final NotificationSettingsService settings;
    private final UserRepository userRepository;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);

    public DefaultNotificationService(NotificationSettingsService settings, UserRepository userRepository,
                                      List<NotificationSender> senders) {
        this.settings = settings;
        this.userRepository = userRepository;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
    }

    @Override
    @Transactional
    public void notify(UUID recipientId, NotificationType type, String title, String message, String link) {
        EffectiveNotificationSetting setting = settings.effective(type);
        if (!setting.enabled()) {
            return;
        }
        List<User> recipients = userRepository.findById(recipientId).filter(User::isActive).stream().toList();
        deliver(recipients, setting.channels(), new NotificationContent(type, title, message, link));
    }

    @Override
    @Transactional
    public void notifyConfiguredRecipients(NotificationType type, String title, String message, String link) {
        EffectiveNotificationSetting setting = settings.effective(type);
        if (setting.enabled()) {
            deliver(configuredRecipients(setting), setting.channels(), new NotificationContent(type, title, message, link));
        }
    }

    @Override
    @Transactional
    public void notifyConfiguredRecipients(NotificationType type, String title, String message, String link,
                                           Set<NotificationChannel> channels) {
        if (channels == null) {
            notifyConfiguredRecipients(type, title, message, link);
            return;
        }
        EffectiveNotificationSetting setting = settings.effective(type);
        deliver(configuredRecipients(setting), channels, new NotificationContent(type, title, message, link));
    }

    private List<User> configuredRecipients(EffectiveNotificationSetting setting) {
        return userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(user -> setting.recipientRoles().contains(user.getRole())
                        || setting.recipientUserIds().contains(user.getId()))
                .toList();
    }

    private void deliver(List<User> recipients, Set<NotificationChannel> channels, NotificationContent content) {
        if (recipients.isEmpty()) {
            return;
        }
        // En el orden del enum: la campanita primero, después lo que sale de la aplicación.
        for (NotificationChannel channel : NotificationChannel.values()) {
            NotificationSender sender = senders.get(channel);
            if (channels.contains(channel) && sender != null) {
                sender.send(recipients, content);
            }
        }
    }
}
