package org.example.exception;

import org.example.dto.FieldValidationErrorDetail;

import java.util.List;

/** 422 del dominio de órdenes de trabajo -- ver claude/CAM-14-ordenes-de-trabajo.md. */
public class WorkOrderValidationException extends RuntimeException {

    private final List<FieldValidationErrorDetail> details;

    public WorkOrderValidationException(String message, List<FieldValidationErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<FieldValidationErrorDetail> getDetails() {
        return details;
    }
}
