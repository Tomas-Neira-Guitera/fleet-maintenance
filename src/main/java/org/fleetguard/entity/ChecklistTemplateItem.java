package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.fleetguard.entity.checklist.ChecklistItemType;
import org.fleetguard.entity.checklist.ChecklistSection;

import java.util.UUID;

// CAM-150: un control de una plantilla, con las mismas reglas que un control propio del vehículo.
@Entity
@Table(name = "checklist_template_items")
public class ChecklistTemplateItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ChecklistTemplate template;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistSection section;

    @Column(nullable = false)
    private int position;

    protected ChecklistTemplateItem() {
        // JPA
    }

    public ChecklistTemplateItem(ChecklistTemplate template, String label, ChecklistItemType type,
                                 ChecklistSection section, int position) {
        this.template = template;
        this.label = label;
        this.type = type;
        this.section = section;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public ChecklistTemplate getTemplate() {
        return template;
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

    public int getPosition() {
        return position;
    }
}
