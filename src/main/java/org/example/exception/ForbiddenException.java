package org.example.exception;

/** 403: token válido, pero el rol del usuario no tiene permiso para este endpoint (CAM-23). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
        super("No tenés permiso para hacer esto.");
    }
}
