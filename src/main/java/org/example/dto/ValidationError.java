package org.example.dto;

import java.util.List;

/** Matches components.schemas.ValidationError in openapi.yaml (ApiError + details[]). */
public record ValidationError(String error, String message, List<ValidationErrorDetail> details) {
}
