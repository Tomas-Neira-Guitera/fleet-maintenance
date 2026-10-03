package org.fleetguard.exception;

import java.util.List;

import org.fleetguard.dto.FieldValidationErrorDetail;

/** Respuestas 422 del ABM de vehículos -- CAM-25. */
public class VehicleValidationException extends RuntimeException {

    private final List<FieldValidationErrorDetail> details;

    public VehicleValidationException(String message, List<FieldValidationErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<FieldValidationErrorDetail> getDetails() {
        return details;
    }
}
