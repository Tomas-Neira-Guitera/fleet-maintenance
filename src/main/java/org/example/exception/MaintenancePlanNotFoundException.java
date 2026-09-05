package org.example.exception;

public class MaintenancePlanNotFoundException extends RuntimeException {
    public MaintenancePlanNotFoundException(String planId) {
        super("No existe un plan de mantenimiento con id " + planId);
    }
}
