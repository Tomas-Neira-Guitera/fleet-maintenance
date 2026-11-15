package org.fleetguard.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orden de trabajo generada para resolver un mantenimiento programado o un defecto, o
 * abierta suelta. Ver claude/CAM-14-ordenes-de-trabajo.md en el Project. Registra qué se
 * hizo, quién lo hizo, si fue con personal propio o externo, y (vía work_order_expenses/
 * work_order_photos) el costo y la evidencia del arreglo.
 */
@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private WorkOrderSourceType sourceType;

    /** Seteado solo si nació de una fila del calendario de mantenimientos (CAM-42). */
    @Column(name = "scheduled_maintenance_id")
    private UUID scheduledMaintenanceId;

    /** Seteado si el origen (directo o vía scheduled_maintenance) es un defecto. */
    @Column(name = "defect_id")
    private UUID defectId;

    /** Seteado si el origen (directo o vía scheduled_maintenance) es una asignación de plan. */
    @Column(name = "assignment_id")
    private UUID assignmentId;

    /** Denormalizado (nombre del plan, descripción del defecto, o texto libre si manual). */
    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false)
    private WorkOrderExecutionType executionType;

    @Column(name = "external_provider")
    private String externalProvider;

    /** Texto libre: en OTs externas, el contacto en el proveedor. En internas, lo reemplaza technicianId (CAM-60). */
    private String assignee;

    /** Usuario con rol TECNICO a cargo (CAM-60). Solo en OTs internas; null si no hay técnico asignado. */
    @Column(name = "technician_id")
    private UUID technicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status = WorkOrderStatus.ASIGNADA;

    @Column(name = "closing_description", columnDefinition = "text")
    private String closingDescription;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    // CAM-148: la pausa actual, mientras la OT está en espera (null si no lo está), y los segundos
    // que estuvo en espera en las pausas ya terminadas (null es 0: las OTs de antes no lo tienen,
    // y una columna nueva NOT NULL no se agrega bien con ddl-auto en una base que ya existe).
    @Enumerated(EnumType.STRING)
    @Column(name = "pause_reason", length = 30)
    private WorkOrderPauseReason pauseReason;

    @Column(name = "pause_comment", columnDefinition = "text")
    private String pauseComment;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_seconds")
    private Long pausedSeconds;

    /** CAM-148: el registro de cada pausa. Lo maneja la OT, así se cierra la pausa venga de donde venga el cambio. */
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("startedAt")
    private List<WorkOrderPause> pauses = new ArrayList<>();

    protected WorkOrder() {
        // JPA
    }

    public WorkOrder(UUID vehicleId, WorkOrderSourceType sourceType, UUID scheduledMaintenanceId, UUID defectId,
                      UUID assignmentId, String title, String description, WorkOrderExecutionType executionType,
                      String externalProvider, String assignee, UUID technicianId) {
        this.vehicleId = vehicleId;
        this.sourceType = sourceType;
        this.scheduledMaintenanceId = scheduledMaintenanceId;
        this.defectId = defectId;
        this.assignmentId = assignmentId;
        this.title = title;
        this.description = description;
        this.executionType = executionType;
        this.externalProvider = externalProvider;
        this.assignee = assignee;
        this.technicianId = technicianId;
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

    public WorkOrderSourceType getSourceType() {
        return sourceType;
    }

    public UUID getScheduledMaintenanceId() {
        return scheduledMaintenanceId;
    }

    /** CAM-60: al reusar una OT abierta desde una programación nueva, pasa a apuntar a la vigente. */
    public void setScheduledMaintenanceId(UUID scheduledMaintenanceId) {
        this.scheduledMaintenanceId = scheduledMaintenanceId;
        touch();
    }

    public UUID getDefectId() {
        return defectId;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public WorkOrderExecutionType getExecutionType() {
        return executionType;
    }

    public void setExecutionType(WorkOrderExecutionType executionType) {
        this.executionType = executionType;
        touch();
    }

    public String getExternalProvider() {
        return externalProvider;
    }

    public void setExternalProvider(String externalProvider) {
        this.externalProvider = externalProvider;
        touch();
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
        touch();
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(UUID technicianId) {
        this.technicianId = technicianId;
        touch();
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public String getClosingDescription() {
        return closingDescription;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    /** De asignada a en proceso, o de vuelta desde en espera (CAM-148), que cierra la pausa. */
    public void start() {
        endPause(Instant.now());
        this.status = WorkOrderStatus.EN_PROCESO;
        touch();
    }

    /** CAM-148: pasa a en espera, con el motivo. Queda registrada la pausa. */
    public void pause(WorkOrderPauseReason reason, String comment) {
        Instant now = Instant.now();
        this.status = WorkOrderStatus.EN_ESPERA;
        this.pauseReason = reason;
        this.pauseComment = comment;
        this.pausedAt = now;
        pauses.add(new WorkOrderPause(this, reason, comment, now));
        touch();
    }

    /** Suma la pausa en curso al total, cierra su registro y la limpia. Sin pausa en curso no hace nada. */
    private void endPause(Instant at) {
        if (pausedAt == null) {
            return;
        }
        long seconds = Math.max(0, Duration.between(pausedAt, at).getSeconds());
        this.pausedSeconds = (pausedSeconds == null ? 0 : pausedSeconds) + seconds;
        pauses.stream().filter(pause -> pause.getEndedAt() == null).forEach(pause -> pause.end(at));
        this.pauseReason = null;
        this.pauseComment = null;
        this.pausedAt = null;
    }

    public List<WorkOrderPause> getPauses() {
        return pauses;
    }

    public void finalizeOrder(String closingDescription) {
        this.status = WorkOrderStatus.FINALIZADA;
        this.closingDescription = closingDescription;
        this.finalizedAt = Instant.now();
        touch();
    }

    /** Desde en espera también (CAM-148): la pausa se cierra en el momento de cancelar. */
    public void cancel() {
        endPause(Instant.now());
        this.status = WorkOrderStatus.CANCELADA;
        touch();
    }

    /** true mientras se puede seguir editando/agregando gastos y fotos (asignada, en proceso o en espera). */
    public boolean isOpen() {
        return status.isOpen();
    }

    public WorkOrderPauseReason getPauseReason() {
        return pauseReason;
    }

    public String getPauseComment() {
        return pauseComment;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    /** Segundos en espera hasta {@code now}: las pausas terminadas más la actual, si sigue en espera. */
    public long pausedSecondsUntil(Instant now) {
        long total = pausedSeconds == null ? 0 : pausedSeconds;
        if (pausedAt != null) {
            total += Math.max(0, Duration.between(pausedAt, now).getSeconds());
        }
        return total;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
