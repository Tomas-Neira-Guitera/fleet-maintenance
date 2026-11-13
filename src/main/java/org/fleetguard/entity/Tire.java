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


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TireStatus status = TireStatus.STOCK;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_reason")
    private String retiredReason;


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Tire() {
        // JPA
    }

    public Tire(String code, String brand, String model, String size, LocalDate purchaseDate, BigDecimal cost) {
        update(code, brand, model, size, purchaseDate, cost);
        this.createdAt = Instant.now();
    }

    public final void update(String code, String brand, String model, String size, LocalDate purchaseDate,
                             BigDecimal cost) {
        this.code = code;
        this.brand = brand;
        this.model = model;
        this.size = size;
        this.purchaseDate = purchaseDate;
        this.cost = cost;
    }

    public void mount() {
        this.status = TireStatus.MOUNTED;
    }

    public void toStock() {
        this.status = TireStatus.STOCK;
    }

    public void retire(Instant at, String reason) {
        this.status = TireStatus.RETIRED;
        this.retiredAt = at;
        this.retiredReason = reason;
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


    public TireStatus getStatus() {
        return status;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public String getRetiredReason() {
        return retiredReason;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }
}
