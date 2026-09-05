package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Asignación de un plan del catálogo a un vehículo puntual -- la entidad "viva" que
 * guarda cuándo se hizo la última vez y cuándo vence la próxima. No se expone como
 * recurso propio de la API: siempre se accede vía /api/vehicles/{vehicleId}/... (ver
 * CAM-40-maintenance-api-contract.md, división de recursos). El historial completo de
 * veces que se hizo vive en MaintenanceCompletion -- estos campos son un cache de la
 * última completion, no la fuente de verdad.
 */
@Entity
@Table(name = "vehicle_maintenance_assignments")
public class VehicleMaintenanceAssignment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "maintenance_plan_id", nullable = false)
    private MaintenancePlan maintenancePlan;

    @Column(name = "last_done_km")
    private Long lastDoneKm;

    @Column(name = "last_done_date")
    private LocalDate lastDoneDate;

    @Column(name = "next_due_km")
    private Long nextDueKm;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(nullable = false)
    private boolean active = true;

    protected VehicleMaintenanceAssignment() {
        // JPA
    }

    public VehicleMaintenanceAssignment(UUID vehicleId, MaintenancePlan maintenancePlan, Long lastDoneKm, LocalDate lastDoneDate) {
        this.vehicleId = vehicleId;
        this.maintenancePlan = maintenancePlan;
        this.lastDoneKm = lastDoneKm;
        this.lastDoneDate = lastDoneDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public MaintenancePlan getMaintenancePlan() {
        return maintenancePlan;
    }

    public Long getLastDoneKm() {
        return lastDoneKm;
    }

    public void setLastDoneKm(Long lastDoneKm) {
        this.lastDoneKm = lastDoneKm;
    }

    public LocalDate getLastDoneDate() {
        return lastDoneDate;
    }

    public void setLastDoneDate(LocalDate lastDoneDate) {
        this.lastDoneDate = lastDoneDate;
    }

    public Long getNextDueKm() {
        return nextDueKm;
    }

    public void setNextDueKm(Long nextDueKm) {
        this.nextDueKm = nextDueKm;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Recalcula next_due_* a partir de last_done_* y el intervalo del plan. */
    public void recalculateNextDue() {
        Integer intervalKm = maintenancePlan.getIntervalKm();
        Integer intervalDays = maintenancePlan.getIntervalDays();
        this.nextDueKm = (lastDoneKm != null && intervalKm != null) ? lastDoneKm + intervalKm : null;
        this.nextDueDate = (lastDoneDate != null && intervalDays != null) ? lastDoneDate.plusDays(intervalDays) : null;
    }
}
