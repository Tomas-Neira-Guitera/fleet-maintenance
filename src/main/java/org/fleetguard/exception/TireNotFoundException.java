package org.fleetguard.exception;

public class TireNotFoundException extends RuntimeException {
    public TireNotFoundException(String id) {
        super("No existe un neumático con id " + id);
    }
}
