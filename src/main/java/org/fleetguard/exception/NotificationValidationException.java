package org.fleetguard.exception;

import org.fleetguard.dto.FieldValidationErrorDetail;

import java.util.List;

/** 422 de la configuración de avisos (CAM-117, CAM-118). */
public class NotificationValidationException extends RuntimeException {

    private final List<FieldValidationErrorDetail> details;

    public NotificationValidationException(String message, List<FieldValidationErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<FieldValidationErrorDetail> getDetails() {
        return details;
    }
}
