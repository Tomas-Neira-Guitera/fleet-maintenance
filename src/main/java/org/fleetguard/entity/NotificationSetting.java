package org.fleetguard.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Configuración de un tipo de aviso (CAM-117), una fila por tipo. Sin fila rige el default de
 * NotificationCatalog. La anticipación (leadDays/leadKm) solo la usa MAINTENANCE_DUE_SOON (CAM-118).
 */
@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private NotificationType type;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "channel_in_app", nullable = false)
    private boolean inApp;

    @Column(name = "channel_push", nullable = false)
    private boolean push;

    @Column(name = "channel_email", nullable = false)
    private boolean email;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_setting_roles", joinColumns = @JoinColumn(name = "type"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> recipientRoles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_setting_users", joinColumns = @JoinColumn(name = "type"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> recipientUserIds = new HashSet<>();

    @Column(name = "lead_days")
    private Integer leadDays;

    @Column(name = "lead_km")
    private Integer leadKm;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationSetting() {
        // JPA
    }

    public NotificationSetting(NotificationType type) {
        this.type = type;
    }

    public void apply(boolean enabled, Set<NotificationChannel> channels, Set<Role> roles, Set<UUID> userIds,
                      Integer leadDays, Integer leadKm, Instant now) {
        this.enabled = enabled;
        this.inApp = channels.contains(NotificationChannel.IN_APP);
        this.push = channels.contains(NotificationChannel.PUSH);
        this.email = channels.contains(NotificationChannel.EMAIL);
        this.recipientRoles.clear();
        this.recipientRoles.addAll(roles);
        this.recipientUserIds.clear();
        this.recipientUserIds.addAll(userIds);
        this.leadDays = leadDays;
        this.leadKm = leadKm;
        this.updatedAt = now;
    }

    public NotificationType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<NotificationChannel> getChannels() {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (inApp) channels.add(NotificationChannel.IN_APP);
        if (push) channels.add(NotificationChannel.PUSH);
        if (email) channels.add(NotificationChannel.EMAIL);
        return channels;
    }

    public Set<Role> getRecipientRoles() {
        return Set.copyOf(recipientRoles);
    }

    public Set<UUID> getRecipientUserIds() {
        return Set.copyOf(recipientUserIds);
    }

    public Integer getLeadDays() {
        return leadDays;
    }

    public Integer getLeadKm() {
        return leadKm;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
