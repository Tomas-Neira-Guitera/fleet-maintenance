package org.example.exception;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(String vehicleId) {
        super("No existe un vehículo con id " + vehicleId);
    }
}
