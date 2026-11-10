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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// CAM-157: profundidad del dibujo medida en una cubierta, en el pre-viaje del chofer o por el admin.
@Entity
@Table(name = "tire_tread_readings")
public class TireTreadReading {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tire_id", nullable = false)
    private Tire tire;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TirePosition position;

    @Column(name = "depth_mm", nullable = false, precision = 4, scale = 1)
    private BigDecimal depthMm;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "measured_by")
    private UUID measuredBy;

    @Column(name = "measured_by_name")
    private String measuredByName;

    // La inspección en la que se midió; null si la cargó el admin.
    @Column(name = "inspection_id")
    private UUID inspectionId;

    protected TireTreadReading() {
        // JPA
    }

    public TireTreadReading(Tire tire, UUID vehicleId, TirePosition position, BigDecimal depthMm, Instant measuredAt,
                            UUID measuredBy, String measuredByName, UUID inspectionId) {
        this.tire = tire;
        this.vehicleId = vehicleId;
        this.position = position;
        this.depthMm = depthMm;
        this.measuredAt = measuredAt;
        this.measuredBy = measuredBy;
        this.measuredByName = measuredByName;
        this.inspectionId = inspectionId;
    }

    public UUID getId() {
        return id;
    }

    public Tire getTire() {
        return tire;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public TirePosition getPosition() {
        return position;
    }

    public BigDecimal getDepthMm() {
        return depthMm;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public UUID getMeasuredBy() {
        return measuredBy;
    }

    public String getMeasuredByName() {
        return measuredByName;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }
}
