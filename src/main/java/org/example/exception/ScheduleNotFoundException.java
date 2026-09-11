package org.example.exception;

public class ScheduleNotFoundException extends RuntimeException {
    public ScheduleNotFoundException(String scheduleId) {
        super("No existe un mantenimiento programado con id " + scheduleId);
    }
}
