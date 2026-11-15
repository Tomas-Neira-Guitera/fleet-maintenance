package org.fleetguard.exception;

public class ChecklistTemplateNotFoundException extends RuntimeException {
    public ChecklistTemplateNotFoundException(String templateId) {
        super("No existe una plantilla de checklist con id " + templateId);
    }
}
