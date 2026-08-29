package org.example.exception;

public class PhotoTooLargeException extends RuntimeException {
    public PhotoTooLargeException(String message) {
        super(message);
    }
}
