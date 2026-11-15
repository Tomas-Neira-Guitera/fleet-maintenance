package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Qué turno de taller ya se le avisó al chofer, y para qué fecha (CAM-143). Se avisa una sola vez por
 * fecha: si el turno se reprograma, su scheduledAt cambia y se vuelve a avisar.
 */
@Entity
@Table(name = "scheduled_maintenance_alerts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_id", "scheduled_at"}))
public class ScheduledMaintenanceAlert {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected ScheduledMaintenanceAlert() {
        // JPA
    }

    public ScheduledMaintenanceAlert(UUID scheduleId, Instant scheduledAt, Instant sentAt) {
        this.scheduleId = scheduleId;
        this.scheduledAt = scheduledAt;
        this.sentAt = sentAt;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
