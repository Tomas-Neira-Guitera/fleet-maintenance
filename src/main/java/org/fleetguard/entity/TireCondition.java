package org.fleetguard.entity;

import java.util.Arrays;

/** Lo que el chofer ve en una cubierta, a simple vista (sin medir el dibujo). Del más grave al más leve. */
public enum TireCondition {
    SE_VE_LA_TELA("Se ve la tela o el alambre"),
    BULTO("Bulto o deformación"),
    CORTE_FLANCO("Corte o daño en el flanco"),
    PINCHADA("Pinchada"),
    BAJA_PRESION("Baja presión o desinflada"),
    DESGASTE_IRREGULAR("Desgaste irregular"),
    POCO_DIBUJO("Poco dibujo");

    private final String label;

    TireCondition(String label) {
        this.label = label;
    }

    public static TireCondition fromJson(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values()).filter(c -> c.toJson().equals(value.trim())).findFirst().orElse(null);
    }

    public String toJson() {
        return name().toLowerCase().replace('_', '-');
    }

    public String label() {
        return label;
    }
}
