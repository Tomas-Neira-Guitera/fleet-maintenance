package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * CAM-149: un comentario en una orden de trabajo, entre el admin y el técnico. No se edita ni se borra: es
 * el registro de lo que se habló. El autor sale del JWT; su nombre y su rol quedan guardados como estaban,
 * por si después cambian.
 */
@Entity
@Table(name = "work_order_comments")
public class WorkOrderComment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, columnDefinition = "text")
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 20)
    private Role authorRole;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkOrderComment() {
        // JPA
    }

    public WorkOrderComment(WorkOrder workOrder, UUID authorId, String authorName, Role authorRole, String text,
                            Instant createdAt) {
        this.workOrder = workOrder;
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorRole = authorRole;
        this.text = text;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Role getAuthorRole() {
        return authorRole;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
