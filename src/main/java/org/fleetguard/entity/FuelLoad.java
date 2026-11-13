package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// CAM-156: una carga de combustible. El consumo se calcula entre dos cargas de tanque lleno; no se guarda.
@Entity
@Table(name = "fuel_loads")
public class FuelLoad {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "loaded_at", nullable = false)
    private Instant loadedAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal liters;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "odometer_km", nullable = false)
    private long odometerKm;

    @Column(name = "full_tank", nullable = false)
    private boolean fullTank;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Si al cargarla el consumo del tramo que cierra dio muy por encima del promedio: con cuánto y contra qué.
    @Column(name = "alert_liters_per_100_km", precision = 8, scale = 2)
    private BigDecimal alertLitersPer100Km;

    @Column(name = "alert_average_liters_per_100_km", precision = 8, scale = 2)
    private BigDecimal alertAverageLitersPer100Km;

    protected FuelLoad() {
        // JPA
    }

    public FuelLoad(Vehicle vehicle, Instant loadedAt, BigDecimal liters, BigDecimal amount, long odometerKm,
                    boolean fullTank, UUID createdBy) {
        update(vehicle, loadedAt, liters, amount, odometerKm, fullTank);
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public final void update(Vehicle vehicle, Instant loadedAt, BigDecimal liters, BigDecimal amount, long odometerKm,
                             boolean fullTank) {
        this.vehicle = vehicle;
        this.loadedAt = loadedAt;
        this.liters = liters;
        this.amount = amount;
        this.odometerKm = odometerKm;
        this.fullTank = fullTank;
    }

    public void markAlert(BigDecimal litersPer100Km, BigDecimal averageLitersPer100Km) {
        this.alertLitersPer100Km = litersPer100Km;
        this.alertAverageLitersPer100Km = averageLitersPer100Km;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public BigDecimal getLiters() {
        return liters;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getOdometerKm() {
        return odometerKm;
    }

    public boolean isFullTank() {
        return fullTank;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getAlertLitersPer100Km() {
        return alertLitersPer100Km;
    }

    public BigDecimal getAlertAverageLitersPer100Km() {
        return alertAverageLitersPer100Km;
    }
}
