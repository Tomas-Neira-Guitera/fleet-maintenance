package org.example.exception;

import org.example.dto.ApiError;
import org.example.dto.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Formato de error uniforme en toda la API: {error, message} + details[] para
 * validación (ver CAM-11-dvir-contract.md sección 6). Es el único lugar donde
 * se mapean nuevas excepciones de dominio a respuestas HTTP.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<ApiError> handleVehicleNotFound(VehicleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("VEHICLE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(VehicleStateConflictException.class)
    public ResponseEntity<ApiError> handleVehicleStateConflict(VehicleStateConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InspectionValidationException.class)
    public ResponseEntity<ValidationError> handleInspectionValidation(InspectionValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidationError("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MissingDriverHeaderException.class)
    public ResponseEntity<ApiError> handleMissingDriverHeader(MissingDriverHeaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("MISSING_DRIVER_HEADER", ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedPhotoTypeException.class)
    public ResponseEntity<ApiError> handleUnsupportedPhotoType(UnsupportedPhotoTypeException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ApiError("UNSUPPORTED_MEDIA_TYPE", ex.getMessage()));
    }

    @ExceptionHandler(PhotoTooLargeException.class)
    public ResponseEntity<ApiError> handlePhotoTooLarge(PhotoTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("PAYLOAD_TOO_LARGE", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("PAYLOAD_TOO_LARGE", "La foto supera el tamaño máximo permitido (8MB)."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Ocurrió un error inesperado."));
    }
}
