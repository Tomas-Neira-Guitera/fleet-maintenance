package org.fleetguard.service;

// CAM-79: largo máximo de los títulos que se ven en calendario, listados y tarjetas.
public final class TextLimits {

    public static final int TITLE_MAX_LENGTH = 30;
    // CAM-32: descripciones largas que se pueden dictar (detalle de un defecto).
    public static final int DESCRIPTION_MAX_LENGTH = 1000;

    private TextLimits() {
    }

    public static boolean exceedsTitle(String value) {
        return value != null && value.trim().length() > TITLE_MAX_LENGTH;
    }

    public static String titleTooLongMessage() {
        return "No puede superar los " + TITLE_MAX_LENGTH + " caracteres";
    }
}
