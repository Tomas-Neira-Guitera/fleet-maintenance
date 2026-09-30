package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.entity.checklist.ChecklistItemType;
import org.example.entity.checklist.ChecklistSection;

import java.time.Instant;
import java.util.UUID;

// CAM-31: ítem extra del checklist pre-trip de un vehículo puntual. Quitarlo es baja lógica
// (active=false) para que las respuestas viejas sigan apuntando a un ítem existente.
@Entity
@Table(name = "vehicle_checklist_items")
public class VehicleChecklistItem {

    public static final String ID_PREFIX = "extra-";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistSection section;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VehicleChecklistItem() {
        // JPA
    }

    public VehicleChecklistItem(UUID vehicleId, String label, ChecklistItemType type, ChecklistSection section) {
        this.vehicleId = vehicleId;
        this.label = label;
        this.type = type;
        this.section = section;
        this.createdAt = Instant.now();
    }

    // itemId que ve el cliente: prefijado para no chocar con los ids del catálogo base.
    public String getItemId() {
        return ID_PREFIX + id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getLabel() {
        return label;
    }

    public ChecklistItemType getType() {
        return type;
    }

    public ChecklistSection getSection() {
        return section;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
