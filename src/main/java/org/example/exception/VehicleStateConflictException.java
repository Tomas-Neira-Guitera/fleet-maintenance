package org.example.exception;

/** Respuestas 409 según CAM-11-dvir-contract.md sección 5: VEHICLE_ON_TRIP / NO_OPEN_TRIP. */
public class VehicleStateConflictException extends RuntimeException {

    private final String errorCode;

    public VehicleStateConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
