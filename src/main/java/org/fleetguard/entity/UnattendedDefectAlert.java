package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Qué defecto ya se avisó como sin atender (CAM-142). Se avisa una sola vez: si después le abren una OT y
 * se cancela, vuelve a marcarse en Defectos pero no se vuelve a avisar.
 */
@Entity
@Table(name = "unattended_defect_alerts")
public class UnattendedDefectAlert {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "defect_id", nullable = false, unique = true)
    private UUID defectId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected UnattendedDefectAlert() {
        // JPA
    }

    public UnattendedDefectAlert(UUID defectId, Instant sentAt) {
        this.defectId = defectId;
        this.sentAt = sentAt;
    }

    public UUID getDefectId() {
        return defectId;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
