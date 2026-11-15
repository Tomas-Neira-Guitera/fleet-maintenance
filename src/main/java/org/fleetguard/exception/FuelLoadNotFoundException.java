package org.fleetguard.exception;

public class FuelLoadNotFoundException extends RuntimeException {
    public FuelLoadNotFoundException(String id) {
        super("No existe una carga de combustible con id " + id);
    }
}
