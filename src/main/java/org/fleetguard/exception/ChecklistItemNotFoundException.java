package org.fleetguard.exception;

public class ChecklistItemNotFoundException extends RuntimeException {
    public ChecklistItemNotFoundException(String itemId) {
        super("No existe un ítem de checklist con id " + itemId + " para este vehículo");
    }
}
