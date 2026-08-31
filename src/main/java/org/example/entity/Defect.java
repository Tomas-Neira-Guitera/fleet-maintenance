package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "defects")
public class Defect {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_answer_id", nullable = false, unique = true)
    private InspectionAnswer inspectionAnswer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectSeverity severity;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    private String photoUrl;

    @Column(nullable = false)
    private Instant createdAt;

    /** Reservado para la futura gestión de defectos; por ahora siempre "open". */
    @Column(nullable = false)
    private String status = "open";

    protected Defect() {
        // JPA
    }

    public Defect(DefectSeverity severity, String description, String photoUrl, Instant createdAt) {
        this.severity = severity;
        this.description = description;
        this.photoUrl = photoUrl;
        this.createdAt = createdAt;
    }

    public void setInspectionAnswer(InspectionAnswer inspectionAnswer) {
        this.inspectionAnswer = inspectionAnswer;
    }

    public UUID getId() {
        return id;
    }

    public DefectSeverity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }
}
