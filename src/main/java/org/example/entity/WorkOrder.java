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

    public void start() {
        this.status = WorkOrderStatus.EN_PROCESO;
        touch();
    }

    public void finalizeOrder(String closingDescription) {
        this.status = WorkOrderStatus.FINALIZADA;
        this.closingDescription = closingDescription;
        this.finalizedAt = Instant.now();
        touch();
    }

    public void cancel() {
        this.status = WorkOrderStatus.CANCELADA;
        touch();
    }

    /** true mientras se puede seguir editando/agregando gastos y fotos (asignada o en_proceso). */
    public boolean isOpen() {
        return status == WorkOrderStatus.ASIGNADA || status == WorkOrderStatus.EN_PROCESO;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
