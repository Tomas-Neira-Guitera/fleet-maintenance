package org.example.exception;

/** Respuestas 409 del dominio de mantenimiento preventivo -- ver CAM-40-maintenance-api-contract.md. */
public class MaintenanceConflictException extends RuntimeException {

    private final String errorCode;

    public MaintenanceConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
