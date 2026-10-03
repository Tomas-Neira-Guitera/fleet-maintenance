package org.fleetguard.entity.checklist;

/** Espejo del ChecklistItemType del frontend ('check' | 'number'). */
public enum ChecklistItemType {
    CHECK,
    NUMBER;

    public static ChecklistItemType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "check" -> CHECK;
            case "number" -> NUMBER;
            default -> null;
        };
    }

    public String toJson() {
        return name().toLowerCase();
    }
}
