package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.fleetguard.entity.checklist.ChecklistItemType;
import org.fleetguard.entity.checklist.ChecklistSection;

import java.time.Instant;
import java.util.UUID;

// CAM-31: ítem propio de un vehículo. active indica si entra en el checklist pre-trip del chofer;
// con active=false queda cargado en el vehículo pero no se revisa en la inspección.
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

    // CAM-159: la plantilla de la que se copió (null si lo cargó el admin). Con esto se puede quitar la plantilla.
    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VehicleChecklistItem() {
        // JPA
    }

    public VehicleChecklistItem(UUID vehicleId, String label, ChecklistItemType type, ChecklistSection section) {
        this(vehicleId, label, type, section, true);
    }

    public VehicleChecklistItem(UUID vehicleId, String label, ChecklistItemType type, ChecklistSection section,
                                boolean active) {
        this(vehicleId, label, type, section, active, null);
    }

    public VehicleChecklistItem(UUID vehicleId, String label, ChecklistItemType type, ChecklistSection section,
                                boolean active, UUID templateId) {
        this.active = active;
        this.templateId = templateId;
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

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }
}
