package org.example.exception;

public class UnsupportedPhotoTypeException extends RuntimeException {
    public UnsupportedPhotoTypeException(String message) {
        super(message);
    }
}
