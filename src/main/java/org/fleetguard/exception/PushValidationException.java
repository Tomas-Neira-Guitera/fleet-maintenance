package org.fleetguard.exception;

import org.fleetguard.dto.FieldValidationErrorDetail;

import java.util.List;

/** 422 de /api/push/subscriptions (CAM-119): la suscripción vino sin endpoint o sin claves. */
public class PushValidationException extends RuntimeException {

    private final List<FieldValidationErrorDetail> details;

    public PushValidationException(String message, List<FieldValidationErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<FieldValidationErrorDetail> getDetails() {
        return details;
    }
}
