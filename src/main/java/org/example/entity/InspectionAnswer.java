package org.example.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;


import java.util.UUID;

@Entity
@Table(name = "inspection_answers")
public class InspectionAnswer {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Enumerated(EnumType.STRING)
    private CheckOutcome outcome;

    private Double numberValue;

    @OneToOne(mappedBy = "inspectionAnswer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Defect defect;

    protected InspectionAnswer() {
        // JPA
    }

    public InspectionAnswer(String itemId, CheckOutcome outcome, Double numberValue) {
        this.itemId = itemId;
        this.outcome = outcome;
        this.numberValue = numberValue;
    }

    public void setInspection(Inspection inspection) {
        this.inspection = inspection;
    }

    public Inspection getInspection() {
        return inspection;
    }

    public void attachDefect(Defect defect) {
        this.defect = defect;
        defect.setInspectionAnswer(this);
    }

    public UUID getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public CheckOutcome getOutcome() {
        return outcome;
    }

    public Double getNumberValue() {
        return numberValue;
    }

    public Defect getDefect() {
        return defect;
    }
}
