package org.fleetguard.exception;

/** 404 de avisos (CAM-96) y de su configuración (CAM-117): NOTIFICATION_NOT_FOUND, NOTIFICATION_TYPE_NOT_FOUND. */
public class NotificationNotFoundException extends RuntimeException {

    private final String errorCode;

    public NotificationNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
