package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String plate;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    /**
     * Tipo de vehículo (camion/furgon/pickup, texto libre por ahora) -- todavía no existe
     * la ficha completa de gestión de flota (feature 5.7), así que queda nullable hasta
     * que esa historia defina un catálogo real.
     */
    @Column(name = "vehicle_type")
    private String vehicleType;

    /**
     * Kilometraje actual del vehículo (feature 5.5, carga semanal manual). No lo toca
     * ninguna inspección DVIR (CAM-11) -- ver CAM-40-modelo-mantenimiento-preventivo.md.
     */
    @Column(name = "odometer_km", columnDefinition = "bigint not null default 0")
    private long odometerKm = 0;

    protected Vehicle() {
        // JPA
    }

    public Vehicle(String plate, String brand, String model) {
        this.plate = plate;
        this.brand = brand;
        this.model = model;
    }

    public UUID getId() {
        return id;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public long getOdometerKm() {
        return odometerKm;
    }

    public void setOdometerKm(long odometerKm) {
        this.odometerKm = odometerKm;
    }
}
