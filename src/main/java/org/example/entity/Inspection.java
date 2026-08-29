package org.example.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspections")
public class Inspection {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "driver_name")
    private String driverName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionType type;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "odometer_km")
    private Double odometerKm;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "has_blocking_defect", nullable = false)
    private boolean hasBlockingDefect;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InspectionAnswer> answers = new ArrayList<>();

    protected Inspection() {
        // JPA
    }

    public Inspection(Trip trip, UUID vehicleId, String driverId, String driverName, InspectionType type,
            Instant timestamp, Double odometerKm, String notes, boolean hasBlockingDefect) {
        this.trip = trip;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.driverName = driverName;
        this.type = type;
        this.timestamp = timestamp;
        this.odometerKm = odometerKm;
        this.notes = notes;
        this.hasBlockingDefect = hasBlockingDefect;
    }

    public void addAnswer(InspectionAnswer answer) {
        answer.setInspection(this);
        answers.add(answer);
    }

    public UUID getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public InspectionType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getOdometerKm() {
        return odometerKm;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isHasBlockingDefect() {
        return hasBlockingDefect;
    }

    public List<InspectionAnswer> getAnswers() {
        return answers;
    }
}
