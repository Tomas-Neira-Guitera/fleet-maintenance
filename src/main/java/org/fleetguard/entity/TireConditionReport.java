package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CAM-157: lo que el chofer vio en una cubierta en una inspección. Vale mientras su defecto esté abierto y la
 * cubierta siga montada; resolver el defecto (o sacar la cubierta) la deja de nuevo sin problemas.
 */
@Entity
@Table(name = "tire_condition_reports")
public class TireConditionReport {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tire_id", nullable = false)
    private Tire tire;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TirePosition position;

    // Nombres del enum separados por coma: son pocos y siempre se leen juntos.
    @Column(nullable = false)
    private String conditions;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "reported_by")
    private UUID reportedBy;

    @Column(name = "reported_by_name")
    private String reportedByName;

    @Column(name = "inspection_id")
    private UUID inspectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_id")
    private Defect defect;

    protected TireConditionReport() {
    }

    public TireConditionReport(Tire tire, Vehicle vehicle, TirePosition position, Collection<TireCondition> conditions,
                               Instant reportedAt, UUID reportedBy, String reportedByName, UUID inspectionId,
                               Defect defect) {
        this.tire = tire;
        this.vehicle = vehicle;
        this.position = position;
        this.conditions = conditions.stream().map(Enum::name).collect(Collectors.joining(","));
        this.reportedAt = reportedAt;
        this.reportedBy = reportedBy;
        this.reportedByName = reportedByName;
        this.inspectionId = inspectionId;
        this.defect = defect;
    }

    /** Sigue valiendo: el defecto no se resolvió y la cubierta está montada. */
    public boolean isActive() {
        return tire.getStatus() == TireStatus.MOUNTED && (defect == null || !"resuelto".equals(defect.getStatus()));
    }

    public UUID getId() {
        return id;
    }

    public Tire getTire() {
        return tire;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public TirePosition getPosition() {
        return position;
    }

    public Set<TireCondition> getConditions() {
        return Arrays.stream(conditions.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> Arrays.stream(TireCondition.values()).filter(c -> c.name().equals(s)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TireCondition.class)));
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public String getReportedByName() {
        return reportedByName;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public Defect getDefect() {
        return defect;
    }
}
