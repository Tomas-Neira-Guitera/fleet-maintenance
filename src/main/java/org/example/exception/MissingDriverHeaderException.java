package org.example.exception;

/** Lanzada por el stand-in temporal de DriverResolver basado en headers. */
public class MissingDriverHeaderException extends RuntimeException {
    public MissingDriverHeaderException(String message) {
        super(message);
    }
}
