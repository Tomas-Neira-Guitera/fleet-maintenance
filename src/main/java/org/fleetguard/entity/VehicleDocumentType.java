package org.fleetguard.entity;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tipos de documento de un vehículo (CAM-127). El admin carga el documento con un nombre libre y el tipo
 * sale de ese nombre (infer); lo que no se reconoce es "otro". required: se marca si falta, no bloquea.
 * renewable: tiene vencimiento propio y se puede vincular a un plan de mantenimiento por tiempo.
 */
public enum VehicleDocumentType {
    VTV("vtv", "VTV / RTO", true, true, "vtv", "rto", "revision tecnica", "verificacion tecnica"),
    SEGURO("seguro", "Seguro", true, true, "seguro", "poliza"),
    CEDULA_AZUL("cedula_azul", "Cédula azul", false, true, "cedula azul", "autorizado"),
    HABILITACION_CARGA("habilitacion_carga", "Habilitación de carga", false, true, "habilitacion de carga", "ruta", "senasa"),
    CEDULA_VERDE("cedula_verde", "Cédula verde", false, false, "cedula verde", "cedula de identificacion"),
    TITULO("titulo", "Título del automotor", false, false, "titulo"),
    OTRO("otro", "Otro", false, false);

    private final String json;
    private final String label;
    private final boolean required;
    private final boolean renewable;
    // Palabras (sin tildes, en minúscula) que, si aparecen en el nombre, lo identifican. El front tiene la misma lista.
    private final List<String> keywords;

    VehicleDocumentType(String json, String label, boolean required, boolean renewable, String... keywords) {
        this.json = json;
        this.label = label;
        this.required = required;
        this.renewable = renewable;
        this.keywords = List.of(keywords);
    }

    /** "Póliza La Segunda" -> SEGURO; "Permiso municipal" -> OTRO. El primer tipo que coincide gana. */
    public static VehicleDocumentType infer(String name) {
        String normalized = normalize(name);
        return Arrays.stream(values())
                .filter(t -> t.keywords.stream().anyMatch(k -> (" " + normalized + " ").contains(" " + k + " ")))
                .findFirst().orElse(OTRO);
    }

    /** Minúsculas, sin tildes y sin signos: para comparar nombres. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static VehicleDocumentType fromJson(String value) {
        return Arrays.stream(values()).filter(t -> t.json.equals(value)).findFirst().orElse(null);
    }

    public static List<VehicleDocumentType> requiredTypes() {
        return Arrays.stream(values()).filter(VehicleDocumentType::isRequired).toList();
    }

    public String toJson() {
        return json;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isRenewable() {
        return renewable;
    }
}
