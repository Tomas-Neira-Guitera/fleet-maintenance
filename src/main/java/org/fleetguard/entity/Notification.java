package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Un aviso en la campanita de un usuario (CAM-96). Descartarlo lo saca del panel pero no lo borra. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_recipient",
        columnList = "recipient_id, dismissed_at, read_at"))
public class Notification {

    public static final int TITLE_MAX_LENGTH = 150;
    public static final int MESSAGE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    @Column(length = 300)
    private String link;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID recipientId, NotificationType type, String title, String message, String link, Instant createdAt) {
        this.recipientId = recipientId;
        this.type = type;
        this.title = truncate(title, TITLE_MAX_LENGTH);
        this.message = truncate(message, MESSAGE_MAX_LENGTH);
        this.link = link;
        this.createdAt = createdAt;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    /** Idempotente: uno ya leído conserva su fecha de lectura. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    /** Descartado cuenta también como leído, así el número de la campanita no lo sigue contando. */
    public void dismiss(Instant now) {
        markRead(now);
        if (dismissedAt == null) {
            dismissedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getDismissedAt() {
        return dismissedAt;
    }
}
