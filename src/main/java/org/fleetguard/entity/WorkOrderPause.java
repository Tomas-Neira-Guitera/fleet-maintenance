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
 * CAM-148: cada vez que una orden de trabajo estuvo en espera, con su motivo, desde cuándo y hasta
 * cuándo. endedAt es null mientras sigue en espera. El total y la pausa actual también quedan en
 * la OT, para mostrarlos en el listado sin una consulta por OT; esto es el registro de cada una.
 */
@Entity
@Table(name = "work_order_pauses")
public class WorkOrderPause {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkOrderPauseReason reason;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected WorkOrderPause() {
        // JPA
    }

    public WorkOrderPause(WorkOrder workOrder, WorkOrderPauseReason reason, String comment, Instant startedAt) {
        this.workOrder = workOrder;
        this.reason = reason;
        this.comment = comment;
        this.startedAt = startedAt;
    }

    public void end(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public UUID getId() {
        return id;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public WorkOrderPauseReason getReason() {
        return reason;
    }

    public String getComment() {
        return comment;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
