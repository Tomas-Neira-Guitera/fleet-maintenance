package org.fleetguard.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String documentId) {
        super("No existe un documento con id " + documentId + " para ese vehículo");
    }
}
