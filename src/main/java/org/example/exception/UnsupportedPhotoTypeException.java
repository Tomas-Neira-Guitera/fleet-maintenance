package org.example.exception;

public class UnsupportedPhotoTypeException extends RuntimeException {
    public UnsupportedPhotoTypeException(String message) {
        super(message);
    }

    /** Mensaje para mostrar tal cual al usuario: nombra el formato como lo conoce (WEBP), no el MIME type. */
    public static UnsupportedPhotoTypeException wrongType(String contentType) {
        String received = contentType == null || !contentType.contains("/")
                ? "de un formato que no reconocemos"
                : contentType.substring(contentType.indexOf('/') + 1).split("\\+")[0].toUpperCase();
        return new UnsupportedPhotoTypeException(
                "La foto tiene que ser JPG o PNG. El archivo que elegiste es " + received + ".");
    }
}
