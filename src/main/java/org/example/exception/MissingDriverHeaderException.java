package org.example.exception;

/** Thrown by the temporary header-based DriverResolver stand-in. */
public class MissingDriverHeaderException extends RuntimeException {
    public MissingDriverHeaderException(String message) {
        super(message);
    }
}
