package org.example.dto;

import java.util.List;

/**
 * Forma uniforme de los 422 de validación en toda la API: ApiError + details[].
 * Genérico en el tipo de detail porque no todos los dominios describen el campo
 * inválido de la misma manera -- CAM-11 usa {@link ValidationErrorDetail} (itemId de
 * checklist), CAM-40 usa {@link FieldValidationErrorDetail} (nombre de campo genérico).
 * Coincide con components.schemas.ValidationError / FieldValidationError de openapi.yaml.
 */
public record ValidationError<T>(String error, String message, List<T> details) {
}
