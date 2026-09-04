package org.example.exception;

import java.util.List;

import org.example.dto.FieldValidationErrorDetail;

/** Respuestas 422 del dominio de mantenimiento preventivo -- ver CAM-40-maintenance-api-contract.md. */
public class MaintenanceValidationException extends RuntimeException {

    private final List<FieldValidationErrorDetail> details;

    public MaintenanceValidationException(String message, List<FieldValidationErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<FieldValidationErrorDetail> getDetails() {
        return details;
    }
}
