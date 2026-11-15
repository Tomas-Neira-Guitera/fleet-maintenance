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

    // CAM-152: la marca de km fuera de lo normal, con los números del momento del cierre. Sin marca,
    // todo en null. tripKm y averageKm faltan si el motivo es BELOW_VEHICLE_KM y no hubo promedio.
    @Enumerated(EnumType.STRING)
    @Column(name = "km_alert_reason", length = 30)
    private TripKmAlertReason kmAlertReason;

    @Column(name = "km_alert_trip_km")
    private Long kmAlertTripKm;

    @Column(name = "km_alert_average_km")
    private Long kmAlertAverageKm;

    /** El km que declaró el chofer al cerrar. */
    @Column(name = "km_alert_declared_km")
    private Long kmAlertDeclaredKm;

    /** El km que tenía el vehículo antes del cierre. */
    @Column(name = "km_alert_vehicle_km")
    private Long kmAlertVehicleKm;

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

    /** CAM-152: marca el viaje con un kilometraje fuera de lo normal. */
    public void markKmAlert(TripKmAlertReason reason, Long tripKm, Long averageKm, long declaredKm, long vehicleKm) {
        this.kmAlertReason = reason;
        this.kmAlertTripKm = tripKm;
        this.kmAlertAverageKm = averageKm;
        this.kmAlertDeclaredKm = declaredKm;
        this.kmAlertVehicleKm = vehicleKm;
    }

    public TripKmAlertReason getKmAlertReason() {
        return kmAlertReason;
    }

    public Long getKmAlertTripKm() {
        return kmAlertTripKm;
    }

    public Long getKmAlertAverageKm() {
        return kmAlertAverageKm;
    }

    public Long getKmAlertDeclaredKm() {
        return kmAlertDeclaredKm;
    }

    public Long getKmAlertVehicleKm() {
        return kmAlertVehicleKm;
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
