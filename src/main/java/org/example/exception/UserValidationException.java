package org.example.exception;

import org.example.dto.FieldValidationErrorDetail;

import java.util.List;

/** 422 del listado de usuarios (CAM-60), p. ej. un filtro de rol que no existe. */
public class UserValidationException extends RuntimeException {

    private final List<FieldValidationErrorDetail> details;

    public UserValidationException(String message, List<FieldValidationErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<FieldValidationErrorDetail> getDetails() {
        return details;
    }
}
