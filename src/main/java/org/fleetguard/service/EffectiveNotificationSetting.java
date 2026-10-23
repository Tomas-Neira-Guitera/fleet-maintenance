package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;

import java.util.Set;
import java.util.UUID;

/** La configuración que rige para un tipo de aviso: la guardada o, si no hay, la de NotificationCatalog. */
public record EffectiveNotificationSetting(
        NotificationType type,
        boolean enabled,
        Set<NotificationChannel> channels,
        Set<Role> recipientRoles,
        Set<UUID> recipientUserIds,
        Integer leadDays,
        Integer leadKm,
        boolean customized
) {
}
