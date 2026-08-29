package org.example.entity;

public enum InspectionType {
    PRE_TRIP,
    POST_TRIP;

    public static InspectionType fromJson(String value) {
        if ("pre-trip".equals(value)) {
            return PRE_TRIP;
        }
        if ("post-trip".equals(value)) {
            return POST_TRIP;
        }
        return null;
    }

    public String toJson() {
        return this == PRE_TRIP ? "pre-trip" : "post-trip";
    }
}
