package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Un remolque enganchado a lo que lo arrastra. Mientras está abierto (uncoupledAt null), el remolque suma los km
// del tractor o camión. Queda como historial de quién lo llevó.
@Entity
@Table(name = "trailer_couplings")
public class TrailerCoupling {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trailer_id", nullable = false)
    private Vehicle trailer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "towing_id", nullable = false)
    private Vehicle towing;

    @Column(name = "coupled_at", nullable = false)
    private Instant coupledAt;

    @Column(name = "uncoupled_at")
    private Instant uncoupledAt;

    @Column(name = "coupled_by_name")
    private String coupledByName;

    protected TrailerCoupling() {
        // JPA
    }

    public TrailerCoupling(Vehicle trailer, Vehicle towing, Instant coupledAt, String coupledByName) {
        this.trailer = trailer;
        this.towing = towing;
        this.coupledAt = coupledAt;
        this.coupledByName = coupledByName;
    }

    public void uncouple(Instant at) {
        this.uncoupledAt = at;
    }

    public boolean isOpen() {
        return uncoupledAt == null;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getTrailer() {
        return trailer;
    }

    public Vehicle getTowing() {
        return towing;
    }

    public Instant getCoupledAt() {
        return coupledAt;
    }

    public Instant getUncoupledAt() {
        return uncoupledAt;
    }

    public String getCoupledByName() {
        return coupledByName;
    }
}
