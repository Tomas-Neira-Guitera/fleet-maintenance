package org.example.entity;

public enum CheckOutcome {
    OK,
    DEFECT;

    public static CheckOutcome fromJson(String value) {
        if (value == null) {
            return null;
        }
        if ("ok".equals(value)) {
            return OK;
        }
        if ("defect".equals(value)) {
            return DEFECT;
        }
        return null;
    }

    public String toJson() {
        return this == OK ? "ok" : "defect";
    }
}
