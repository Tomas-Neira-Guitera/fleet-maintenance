package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// CAM-157: una cubierta. Sus km no se guardan: salen de sus montajes y del odómetro de cada vehículo.
@Entity
@Table(name = "tires")
public class Tire {

    @Id
    @GeneratedValue
    private UUID id;

    // Número de serie o el que le pinta el taller, para reconocerla; opcional.
    private String code;

    @Column(nullable = false)
    private String brand;

    private String model;

    @Column(nullable = false)
    private String size;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "initial_tread_mm", precision = 4, scale = 1)
    private BigDecimal initialTreadMm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TireStatus status = TireStatus.STOCK;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_reason")
    private String retiredReason;

    // Cuándo se avisó del dibujo bajo; se limpia con una medición buena o al sacarla. Un aviso por vez.
    @Column(name = "tread_alerted_at")
    private Instant treadAlertedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Tire() {
        // JPA
    }

    public Tire(String code, String brand, String model, String size, LocalDate purchaseDate, BigDecimal cost,
                BigDecimal initialTreadMm) {
        update(code, brand, model, size, purchaseDate, cost, initialTreadMm);
        this.createdAt = Instant.now();
    }

    public final void update(String code, String brand, String model, String size, LocalDate purchaseDate,
                             BigDecimal cost, BigDecimal initialTreadMm) {
        this.code = code;
        this.brand = brand;
        this.model = model;
        this.size = size;
        this.purchaseDate = purchaseDate;
        this.cost = cost;
        this.initialTreadMm = initialTreadMm;
    }

    public void mount() {
        this.status = TireStatus.MOUNTED;
    }

    public void toStock() {
        this.status = TireStatus.STOCK;
        this.treadAlertedAt = null;
    }

    public void retire(Instant at, String reason) {
        this.status = TireStatus.RETIRED;
        this.retiredAt = at;
        this.retiredReason = reason;
        this.treadAlertedAt = null;
    }

    public void setTreadAlertedAt(Instant treadAlertedAt) {
        this.treadAlertedAt = treadAlertedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public String getSize() {
        return size;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getInitialTreadMm() {
        return initialTreadMm;
    }

    public TireStatus getStatus() {
        return status;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public String getRetiredReason() {
        return retiredReason;
    }

    public Instant getTreadAlertedAt() {
        return treadAlertedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
