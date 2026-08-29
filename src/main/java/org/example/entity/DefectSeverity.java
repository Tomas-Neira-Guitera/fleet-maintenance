package org.example.entity;

public enum DefectSeverity {
    NON_BLOCKING,
    BLOCKING;

    public static DefectSeverity fromJson(String value) {
        if (value == null) {
            return null;
        }
        if ("non-blocking".equals(value)) {
            return NON_BLOCKING;
        }
        if ("blocking".equals(value)) {
            return BLOCKING;
        }
        return null;
    }

    public String toJson() {
        return this == BLOCKING ? "blocking" : "non-blocking";
    }
}
