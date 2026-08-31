package org.example.dto;

import java.util.List;

/** Coincide con components.schemas.ValidationError de openapi.yaml (ApiError + details[]). */
public record ValidationError(String error, String message, List<ValidationErrorDetail> details) {
}
