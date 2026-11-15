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
import java.time.LocalDate;
import java.util.UUID;

/** Gasto del vehículo que no sale de una orden de trabajo: combustible, seguro, peajes... -- CAM-109. */
@Entity
@Table(name = "vehicle_expenses")
public class VehicleExpense {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /** El día del gasto, que puede ser anterior al de la carga. */
    @Column(name = "expense_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleExpenseCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VehicleExpense() {
        // JPA
    }

    public VehicleExpense(Vehicle vehicle, LocalDate date, VehicleExpenseCategory category, String description,
                          BigDecimal amount) {
        update(vehicle, date, category, description, amount);
        this.createdAt = Instant.now();
    }

    public final void update(Vehicle vehicle, LocalDate date, VehicleExpenseCategory category, String description,
                             BigDecimal amount) {
        this.vehicle = vehicle;
        this.date = date;
        this.category = category;
        this.description = description;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public LocalDate getDate() {
        return date;
    }

    public VehicleExpenseCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
