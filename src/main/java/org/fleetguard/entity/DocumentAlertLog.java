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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Qué aviso de vencimiento de un documento ya salió (CAM-127), para no repetirlo. Va por fecha de
 * vencimiento: si se corrige la fecha del documento, los avisos vuelven a empezar.
 */
@Entity
@Table(name = "document_alert_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_document_alert", columnNames = {"document_id", "stage", "expires_at"}))
public class DocumentAlertLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentAlertStage stage;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected DocumentAlertLog() {
        // JPA
    }

    public DocumentAlertLog(UUID documentId, DocumentAlertStage stage, LocalDate expiresAt, Instant sentAt) {
        this.documentId = documentId;
        this.stage = stage;
        this.expiresAt = expiresAt;
        this.sentAt = sentAt;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public DocumentAlertStage getStage() {
        return stage;
    }
}
