package org.fleetguard.exception;

public class TireNotFoundException extends RuntimeException {
    public TireNotFoundException(String id) {
        super("No existe una cubierta con id " + id);
    }
}
