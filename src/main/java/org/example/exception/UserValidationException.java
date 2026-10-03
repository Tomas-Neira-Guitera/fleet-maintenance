package org.example.exception;

import org.example.dto.FieldValidationErrorDetail;

import java.util.List;

/** 422 de /api/users (CAM-60, CAM-23): rol inexistente, usuario o contraseña inválidos. */
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
