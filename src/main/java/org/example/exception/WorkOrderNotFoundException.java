package org.example.exception;

public class WorkOrderNotFoundException extends RuntimeException {
    public WorkOrderNotFoundException(String workOrderId) {
        super("No existe una orden de trabajo con id " + workOrderId);
    }
}
