package org.example.exception;

/**
 * Respuestas 409 de la gestión de usuarios (CAM-23): USERNAME_TAKEN, CANNOT_MODIFY_SELF,
 * TECHNICIAN_HAS_OPEN_WORK_ORDERS.
 */
public class UserConflictException extends RuntimeException {

    private final String errorCode;

    public UserConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
