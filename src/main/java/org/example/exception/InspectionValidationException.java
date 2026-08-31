package org.example.exception;

import java.util.List;

import org.example.dto.ValidationErrorDetail;

/** Respuesta 422 según CAM-11-dvir-contract.md sección 4. */
public class InspectionValidationException extends RuntimeException {

    private final List<ValidationErrorDetail> details;

    public InspectionValidationException(String message, List<ValidationErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<ValidationErrorDetail> getDetails() {
        return details;
    }
}
