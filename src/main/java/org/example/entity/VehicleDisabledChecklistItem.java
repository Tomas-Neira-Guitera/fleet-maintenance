package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

// CAM-31: ítem del catálogo base que no aplica a un vehículo puntual.
@Entity
@Table(name = "vehicle_disabled_checklist_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vehicle_id", "base_item_id"}))
public class VehicleDisabledChecklistItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "base_item_id", nullable = false)
    private String baseItemId;

    protected VehicleDisabledChecklistItem() {
        // JPA
    }

    public VehicleDisabledChecklistItem(UUID vehicleId, String baseItemId) {
        this.vehicleId = vehicleId;
        this.baseItemId = baseItemId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getBaseItemId() {
        return baseItemId;
    }
}
