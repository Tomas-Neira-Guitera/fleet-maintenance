package org.fleetguard.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String id) {
        super("No existe un usuario con id " + id);
    }
}
