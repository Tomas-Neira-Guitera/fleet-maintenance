package org.example.exception;

/** Respuestas 409 del dominio de órdenes de trabajo -- ver claude/CAM-14-ordenes-de-trabajo.md. */
public class WorkOrderConflictException extends RuntimeException {

    private final String errorCode;

    public WorkOrderConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
