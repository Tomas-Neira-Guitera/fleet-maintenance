package org.fleetguard.exception;

import org.fleetguard.dto.ApiError;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.ValidationError;
import org.fleetguard.dto.ValidationErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler(ChecklistItemNotFoundException.class)
    public ResponseEntity<ApiError> handleChecklistItemNotFound(ChecklistItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("CHECKLIST_ITEM_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(VehicleStateConflictException.class)
    public ResponseEntity<ApiError> handleVehicleStateConflict(VehicleStateConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InspectionValidationException.class)
    public ResponseEntity<ValidationError<ValidationErrorDetail>> handleInspectionValidation(InspectionValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidationError<>("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_CREDENTIALS", ex.getMessage()));
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

    @ExceptionHandler(MaintenancePlanNotFoundException.class)
    public ResponseEntity<ApiError> handleMaintenancePlanNotFound(MaintenancePlanNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("MAINTENANCE_PLAN_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(AssignmentNotFoundException.class)
    public ResponseEntity<ApiError> handleAssignmentNotFound(AssignmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ASSIGNMENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MaintenanceConflictException.class)
    public ResponseEntity<ApiError> handleMaintenanceConflict(MaintenanceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MaintenanceValidationException.class)
    public ResponseEntity<ValidationError<FieldValidationErrorDetail>> handleMaintenanceValidation(MaintenanceValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidationError<>("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(DefectNotFoundException.class)
    public ResponseEntity<ApiError> handleDefectNotFound(DefectNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("DEFECT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<ApiError> handleScheduleNotFound(ScheduleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("SCHEDULE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(VehicleValidationException.class)
    public ResponseEntity<ValidationError<FieldValidationErrorDetail>> handleVehicleValidation(VehicleValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidationError<>("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(WorkOrderNotFoundException.class)
    public ResponseEntity<ApiError> handleWorkOrderNotFound(WorkOrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("WORK_ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WorkOrderConflictException.class)
    public ResponseEntity<ApiError> handleWorkOrderConflict(WorkOrderConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(WorkOrderValidationException.class)
    public ResponseEntity<ValidationError<FieldValidationErrorDetail>> handleWorkOrderValidation(WorkOrderValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidationError<>("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(UserValidationException.class)
    public ResponseEntity<ValidationError<FieldValidationErrorDetail>> handleUserValidation(UserValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidationError<>("VALIDATION_ERROR", ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(UserConflictException.class)
    public ResponseEntity<ApiError> handleUserConflict(UserConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(UserInactiveException.class)
    public ResponseEntity<ApiError> handleUserInactive(UserInactiveException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("USER_INACTIVE", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", ex.getMessage()));
    }

    // --- Errores del pedido que detecta Spring antes de llegar al controller (CAM-78). Sin estos
    // handlers caían en el genérico de abajo y salían como 500, y el cliente no podía distinguir
    // "mandaste algo mal" de "se cayó el servidor".

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                // Cubre JSON roto y también JSON válido con un dato del tipo equivocado
                // ({"odometerKm": "mucho"}): Jackson falla igual en los dos casos.
                .body(new ApiError("MALFORMED_REQUEST",
                        "El cuerpo del pedido no es un JSON válido o tiene un dato con un tipo equivocado."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("BAD_REQUEST", "Falta el parámetro obligatorio '" + ex.getParameterName() + "'."));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("BAD_REQUEST", "Falta la parte '" + ex.getRequestPartName() + "' del formulario."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("BAD_REQUEST", "El parámetro '" + ex.getName() + "' tiene un valor inválido."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED", "El método " + ex.getMethod() + " no está permitido en esta ruta."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "El tipo de contenido del pedido no está soportado."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "No existe la ruta pedida."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Ocurrió un error inesperado."));
    }
}
