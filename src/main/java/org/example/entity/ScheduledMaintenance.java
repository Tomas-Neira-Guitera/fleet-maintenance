package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Fecha y hora en que se planea hacer un mantenimiento o resolver un defecto -- distinto
 * de cuándo corresponde (VehicleMaintenanceAssignment.nextDue*, calculado) y de cuándo ya
 * se hizo (MaintenanceCompletion, historial). Ver CAM-42-programacion-mantenimientos.md.
 * No es un recurso anidado bajo vehículo/asignación/defecto: el calendario semanal del
 * Resumen necesita listar ambos orígenes de forma unificada.
 */
@Entity
@Table(name = "scheduled_maintenances")
public class ScheduledMaintenance {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ScheduleSourceType sourceType;

    /** Seteado solo si sourceType = ASSIGNMENT. */
    @Column(name = "assignment_id")
    private UUID assignmentId;

    /** Seteado solo si sourceType = DEFECT. */
    @Column(name = "defect_id")
    private UUID defectId;

    /** Denormalizado (nombre del plan o descripción del defecto) para no joinear en el calendario. */
    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScheduledMaintenance() {
        // JPA
    }

    public ScheduledMaintenance(UUID vehicleId, ScheduleSourceType sourceType, UUID assignmentId, UUID defectId,
                                 String title, Instant scheduledAt) {
        this.vehicleId = vehicleId;
        this.sourceType = sourceType;
        this.assignmentId = assignmentId;
        this.defectId = defectId;
        this.title = title;
        this.scheduledAt = scheduledAt;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public ScheduleSourceType getSourceType() {
        return sourceType;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public UUID getDefectId() {
        return defectId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Reprogramar pisa la fecha vigente -- no se guarda historial de reprogramaciones. */
    public void reschedule(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
        this.updatedAt = Instant.now();
    }

    public void markDone() {
        this.status = ScheduleStatus.DONE;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = ScheduleStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
