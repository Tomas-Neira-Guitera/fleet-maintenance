package org.fleetguard.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// CAM-150: plantilla de controles que el admin copia a varios vehículos. Los vehículos no quedan
// vinculados: editarla o borrarla no cambia lo que ya se copió.
@Entity
@Table(name = "checklist_templates")
public class ChecklistTemplate {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ChecklistTemplateItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChecklistTemplate() {
        // JPA
    }

    public ChecklistTemplate(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ChecklistTemplateItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
