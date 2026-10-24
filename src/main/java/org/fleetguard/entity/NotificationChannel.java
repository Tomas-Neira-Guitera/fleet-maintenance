package org.fleetguard.entity;

import java.util.Arrays;

/** Por dónde sale un aviso (CAM-117): la campanita, la push al navegador (CAM-119) o el email. */
public enum NotificationChannel {
    IN_APP("in_app"),
    PUSH("push"),
    EMAIL("email");

    private final String json;

    NotificationChannel(String json) {
        this.json = json;
    }

    public static NotificationChannel fromJson(String value) {
        return Arrays.stream(values()).filter(c -> c.json.equals(value)).findFirst().orElse(null);
    }

    public String toJson() {
        return json;
    }
}
