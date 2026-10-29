package org.fleetguard.entity;

import java.util.Arrays;
import java.util.List;

/**
 * Tipos de documento de un vehículo (CAM-127). required: se marca si falta, pero no bloquea.
 * renewable: tiene vencimiento propio y se puede vincular a un plan de mantenimiento por tiempo.
 */
public enum VehicleDocumentType {
    VTV("vtv", "VTV / RTO", true, true),
    SEGURO("seguro", "Seguro", true, true),
    CEDULA_AZUL("cedula_azul", "Cédula azul", false, true),
    HABILITACION_CARGA("habilitacion_carga", "Habilitación de carga", false, true),
    CEDULA_VERDE("cedula_verde", "Cédula verde", false, false),
    TITULO("titulo", "Título del automotor", false, false),
    OTRO("otro", "Otro", false, false);

    private final String json;
    private final String label;
    private final boolean required;
    private final boolean renewable;

    VehicleDocumentType(String json, String label, boolean required, boolean renewable) {
        this.json = json;
        this.label = label;
        this.required = required;
        this.renewable = renewable;
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
