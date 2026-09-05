package org.example.exception;

public class AssignmentNotFoundException extends RuntimeException {
    public AssignmentNotFoundException(String assignmentId) {
        super("No existe una asignación de mantenimiento con id " + assignmentId + " para ese vehículo");
    }
}
