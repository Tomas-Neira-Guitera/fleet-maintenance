package org.example.exception;

public class DefectNotFoundException extends RuntimeException {
    public DefectNotFoundException(String defectId) {
        super("No existe un defecto con id " + defectId);
    }
}
