package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Foto del trabajo realizado en una orden de trabajo -- admite varias por OT. */
@Entity
@Table(name = "work_order_photos")
public class WorkOrderPhoto {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(name = "photo_url", nullable = false)
    private String photoUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkOrderPhoto() {
        // JPA
    }

    public WorkOrderPhoto(WorkOrder workOrder, String photoUrl) {
        this.workOrder = workOrder;
        this.photoUrl = photoUrl;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
