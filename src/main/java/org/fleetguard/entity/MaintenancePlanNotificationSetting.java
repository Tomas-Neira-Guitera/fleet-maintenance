package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Avisos propios de un plan de mantenimiento (CAM-118). Sin fila, el plan usa la configuración general
 * de MAINTENANCE_DUE_SOON / MAINTENANCE_OVERDUE (CAM-117). Los destinatarios son siempre los generales.
 */
@Entity
@Table(name = "maintenance_plan_notification_settings")
public class MaintenancePlanNotificationSetting {

    @Id
    @Column(name = "plan_id")
    private UUID planId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "lead_days")
    private Integer leadDays;

    @Column(name = "lead_km")
    private Integer leadKm;

    @Column(name = "channel_in_app", nullable = false)
    private boolean inApp;

    @Column(name = "channel_push", nullable = false)
    private boolean push;

    @Column(name = "channel_email", nullable = false)
    private boolean email;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaintenancePlanNotificationSetting() {
        // JPA
    }

    public MaintenancePlanNotificationSetting(UUID planId) {
        this.planId = planId;
    }

    public void apply(boolean enabled, Integer leadDays, Integer leadKm, Set<NotificationChannel> channels, Instant now) {
        this.enabled = enabled;
        this.leadDays = leadDays;
        this.leadKm = leadKm;
        this.inApp = channels.contains(NotificationChannel.IN_APP);
        this.push = channels.contains(NotificationChannel.PUSH);
        this.email = channels.contains(NotificationChannel.EMAIL);
        this.updatedAt = now;
    }

    public UUID getPlanId() {
        return planId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getLeadDays() {
        return leadDays;
    }

    public Integer getLeadKm() {
        return leadKm;
    }

    public Set<NotificationChannel> getChannels() {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (inApp) channels.add(NotificationChannel.IN_APP);
        if (push) channels.add(NotificationChannel.PUSH);
        if (email) channels.add(NotificationChannel.EMAIL);
        return channels;
    }
}
