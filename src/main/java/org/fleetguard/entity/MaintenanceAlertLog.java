package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Qué aviso de vencimiento ya salió (CAM-118), para no repetirlo. El ciclo es el próximo vencimiento
 * de la asignación: cuando se registra el mantenimiento, cambia y los avisos vuelven a empezar.
 */
@Entity
@Table(name = "maintenance_alert_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_maintenance_alert_cycle", columnNames = {"assignment_id", "stage", "cycle_key"}))
public class MaintenanceAlertLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceAlertStage stage;

    @Column(name = "cycle_key", nullable = false, length = 60)
    private String cycleKey;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected MaintenanceAlertLog() {
        // JPA
    }

    public MaintenanceAlertLog(UUID assignmentId, MaintenanceAlertStage stage, String cycleKey, Instant sentAt) {
        this.assignmentId = assignmentId;
        this.stage = stage;
        this.cycleKey = cycleKey;
        this.sentAt = sentAt;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public MaintenanceAlertStage getStage() {
        return stage;
    }

    public String getCycleKey() {
        return cycleKey;
    }
}
