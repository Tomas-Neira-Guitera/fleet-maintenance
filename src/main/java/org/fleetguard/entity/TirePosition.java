package org.fleetguard.entity;

import java.util.Arrays;

/**
 * CAM-157: posición de una cubierta. axle 1 es el delantero; 0, el auxilio. inner: la de adentro de un
 * par dual. El nombre que se muestra depende de la configuración del vehículo (TireLayout.labelOf).
 */
public enum TirePosition {
    DEL_IZQ("del-izq", 1, Side.IZQ, false),
    DEL_DER("del-der", 1, Side.DER, false),
    TRAS_IZQ("tras-izq", 2, Side.IZQ, false),
    TRAS_DER("tras-der", 2, Side.DER, false),
    E2_IZQ_EXT("e2-izq-ext", 2, Side.IZQ, false),
    E2_IZQ_INT("e2-izq-int", 2, Side.IZQ, true),
    E2_DER_INT("e2-der-int", 2, Side.DER, true),
    E2_DER_EXT("e2-der-ext", 2, Side.DER, false),
    E3_IZQ_EXT("e3-izq-ext", 3, Side.IZQ, false),
    E3_IZQ_INT("e3-izq-int", 3, Side.IZQ, true),
    E3_DER_INT("e3-der-int", 3, Side.DER, true),
    E3_DER_EXT("e3-der-ext", 3, Side.DER, false),
    AUXILIO("auxilio", 0, null, false);

    public enum Side { IZQ, DER }

    private final String json;
    private final int axle;
    private final Side side;
    private final boolean inner;

    TirePosition(String json, int axle, Side side, boolean inner) {
        this.json = json;
        this.axle = axle;
        this.side = side;
        this.inner = inner;
    }

    public static TirePosition fromJson(String value) {
        return Arrays.stream(values()).filter(p -> p.json.equals(value)).findFirst().orElse(null);
    }

    public String toJson() {
        return json;
    }

    public int axle() {
        return axle;
    }

    public Side side() {
        return side;
    }

    public boolean inner() {
        return inner;
    }

    /** El auxilio no rueda: mientras está ahí no suma km. */
    public boolean rolls() {
        return this != AUXILIO;
    }
}
