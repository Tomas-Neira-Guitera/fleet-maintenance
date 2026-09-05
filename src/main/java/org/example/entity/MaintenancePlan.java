package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Catálogo de planes de mantenimiento (el "qué se mantiene y cada cuánto"), definido una
 * sola vez y reutilizado entre vehículos -- ver CAM-40-modelo-mantenimiento-preventivo.md.
 * No pertenece a ningún vehículo puntual, por eso es el único recurso de este dominio que
 * se expone top-level en la API (/api/maintenance-plans).
 */
@Entity
@Table(name = "maintenance_plans")
public class MaintenancePlan {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_type", nullable = false)
    private IntervalType intervalType;

    @Column(name = "interval_km")
    private Integer intervalKm;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(nullable = false)
    private boolean active = true;

    protected MaintenancePlan() {
        // JPA
    }

    public MaintenancePlan(String name, String category, IntervalType intervalType, Integer intervalKm, Integer intervalDays) {
        this.name = name;
        this.category = category;
        this.intervalType = intervalType;
        this.intervalKm = intervalKm;
        this.intervalDays = intervalDays;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public IntervalType getIntervalType() {
        return intervalType;
    }

    public void setIntervalType(IntervalType intervalType) {
        this.intervalType = intervalType;
    }

    public Integer getIntervalKm() {
        return intervalKm;
    }

    public void setIntervalKm(Integer intervalKm) {
        this.intervalKm = intervalKm;
    }

    public Integer getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(Integer intervalDays) {
        this.intervalDays = intervalDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
