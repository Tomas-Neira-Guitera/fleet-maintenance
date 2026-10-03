package org.fleetguard.entity.checklist;

/** Espejo del ChecklistSection del frontend. */
public enum ChecklistSection {
    EXTERIOR,
    INTERIOR,
    POSTTRIP;

    public static ChecklistSection fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "exterior" -> EXTERIOR;
            case "interior" -> INTERIOR;
            case "posttrip" -> POSTTRIP;
            default -> null;
        };
    }

    public String toJson() {
        return name().toLowerCase();
    }
}
