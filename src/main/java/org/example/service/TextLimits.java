package org.example.service;

// CAM-79: largo máximo de los títulos que se ven en calendario, listados y tarjetas.
public final class TextLimits {

    public static final int TITLE_MAX_LENGTH = 30;

    private TextLimits() {
    }

    public static boolean exceedsTitle(String value) {
        return value != null && value.trim().length() > TITLE_MAX_LENGTH;
    }

    public static String titleTooLongMessage() {
        return "No puede superar los " + TITLE_MAX_LENGTH + " caracteres";
    }
}
