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
import java.util.UUID;

// CAM-157: dónde estuvo una cubierta y entre qué km del vehículo. Abierto (removedAt null) mientras sigue ahí.
@Entity
@Table(name = "tire_mountings")
public class TireMounting {

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

    @Column(name = "mounted_at", nullable = false)
    private Instant mountedAt;

    @Column(name = "mounted_km", nullable = false)
    private long mountedKm;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_km")
    private Long removedKm;

    protected TireMounting() {
        // JPA
    }

    public TireMounting(Tire tire, Vehicle vehicle, TirePosition position, Instant mountedAt, long mountedKm) {
        this.tire = tire;
        this.vehicle = vehicle;
        this.position = position;
        this.mountedAt = mountedAt;
        this.mountedKm = mountedKm;
    }

    public void close(Instant at, long km) {
        this.removedAt = at;
        this.removedKm = km;
    }

    public boolean isOpen() {
        return removedAt == null;
    }

    /** Km que rodó en este montaje: hasta que se sacó, o hasta el km actual del vehículo si sigue puesta. */
    public long km() {
        if (!position.rolls()) {
            return 0;
        }
        long end = removedKm != null ? removedKm : vehicle.getOdometerKm();
        return Math.max(0, end - mountedKm);
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

    public Instant getMountedAt() {
        return mountedAt;
    }

    public long getMountedKm() {
        return mountedKm;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public Long getRemovedKm() {
        return removedKm;
    }
}
