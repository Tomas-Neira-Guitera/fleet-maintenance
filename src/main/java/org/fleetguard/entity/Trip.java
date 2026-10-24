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

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    private Instant startedAt;

    private Instant endedAt;

    // CAM-87: quién abrió y quién cerró el viaje. Opcionales: los viajes anteriores no lo tienen.
    // El nombre es el del momento; al mostrarlo se prefiere el actual del usuario (por id).
    @Column(name = "opened_by_id")
    private UUID openedById;

    @Column(name = "opened_by_name")
    private String openedByName;

    @Column(name = "closed_by_id")
    private UUID closedById;

    @Column(name = "closed_by_name")
    private String closedByName;

    // CAM-101: cuándo se le recordó al chofer que cerrara el viaje. Un solo recordatorio por viaje.
    // Lo escribe TripRepository.markReminderSent, no el viaje.
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    protected Trip() {
        // JPA
    }

    public Trip(Vehicle vehicle, Instant startedAt) {
        this(vehicle, startedAt, null, null);
    }

    public Trip(Vehicle vehicle, Instant startedAt, UUID openedById, String openedByName) {
        this.vehicle = vehicle;
        this.status = TripStatus.OPEN;
        this.startedAt = startedAt;
        this.openedById = openedById;
        this.openedByName = openedByName;
    }

    public void close(Instant endedAt, UUID closedById, String closedByName) {
        this.status = TripStatus.CLOSED;
        this.endedAt = endedAt;
        this.closedById = closedById;
        this.closedByName = closedByName;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public TripStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public UUID getOpenedById() {
        return openedById;
    }

    public String getOpenedByName() {
        return openedByName;
    }

    public UUID getClosedById() {
        return closedById;
    }

    public String getClosedByName() {
        return closedByName;
    }
}
