package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Qué aviso de defecto recurrente ya salió (CAM-135), para no repetirlo dentro de la misma ventana.
 * Ver docs/api/CAM-135-recurring-defects-contract.md §4.
 */
@Entity
@Table(name = "recurring_defect_alerts",
        indexes = @Index(name = "ix_recurring_defect_alert", columnList = "vehicle_id, item_id, sent_at"))
public class RecurringDefectAlert {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected RecurringDefectAlert() {
        // JPA
    }

    public RecurringDefectAlert(UUID vehicleId, String itemId, Instant sentAt) {
        this.vehicleId = vehicleId;
        this.itemId = itemId;
        this.sentAt = sentAt;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getItemId() {
        return itemId;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
