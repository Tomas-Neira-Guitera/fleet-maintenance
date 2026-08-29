package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
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

    /** Accessory codes (faja/traca/grua/rampa) -- see StringListConverter. */
    @Convert(converter = StringListConverter.class)
    @Column(nullable = false)
    private List<String> accessories = new ArrayList<>();

    protected Vehicle() {
        // JPA
    }

    public Vehicle(String plate, String brand, String model, List<String> accessories) {
        this.plate = plate;
        this.brand = brand;
        this.model = model;
        this.accessories = accessories == null ? new ArrayList<>() : accessories;
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

    public List<String> getAccessories() {
        return accessories;
    }

    public void setAccessories(List<String> accessories) {
        this.accessories = accessories;
    }
}
