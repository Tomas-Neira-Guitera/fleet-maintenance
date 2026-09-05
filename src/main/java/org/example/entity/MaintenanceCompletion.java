package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un registro por cada vez que un mantenimiento efectivamente se hizo. Se acumula, nunca
 * se pisa -- es la fuente de verdad del historial (ver
 * CAM-40-modelo-mantenimiento-preventivo.md sección 2.1).
 */
@Entity
@Table(name = "maintenance_completions")
public class MaintenanceCompletion {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private VehicleMaintenanceAssignment assignment;

    @Column(name = "completed_at", nullable = false)
    private LocalDate completedAt;

    @Column(name = "completed_km")
    private Long completedKm;

    @Column(name = "work_order_id")
    private String workOrderId;

    @Column(columnDefinition = "text")
    private String notes;

    protected MaintenanceCompletion() {
        // JPA
    }

    public MaintenanceCompletion(VehicleMaintenanceAssignment assignment, LocalDate completedAt, Long completedKm, String workOrderId, String notes) {
        this.assignment = assignment;
        this.completedAt = completedAt;
        this.completedKm = completedKm;
        this.workOrderId = workOrderId;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public VehicleMaintenanceAssignment getAssignment() {
        return assignment;
    }

    public LocalDate getCompletedAt() {
        return completedAt;
    }

    public Long getCompletedKm() {
        return completedKm;
    }

    public String getWorkOrderId() {
        return workOrderId;
    }

    public String getNotes() {
        return notes;
    }
}
