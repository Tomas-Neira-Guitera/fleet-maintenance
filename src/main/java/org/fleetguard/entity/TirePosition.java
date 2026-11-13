package org.fleetguard.entity;

import java.util.Arrays;
import java.util.List;

/**
 * CAM-157: posición de una cubierta: eje (1 el delantero; 0 el auxilio), lado y, en un eje dual, la exterior o la
 * interior. Qué posiciones tiene un vehículo y cómo se llaman lo dice su tipo (VehicleType).
 */
public enum TirePosition {
    E1_IZQ(1, Side.IZQ, Wheel.SIMPLE), E1_DER(1, Side.DER, Wheel.SIMPLE),
    E1_IZQ_EXT(1, Side.IZQ, Wheel.EXTERIOR), E1_IZQ_INT(1, Side.IZQ, Wheel.INTERIOR),
    E1_DER_INT(1, Side.DER, Wheel.INTERIOR), E1_DER_EXT(1, Side.DER, Wheel.EXTERIOR),
    E2_IZQ(2, Side.IZQ, Wheel.SIMPLE), E2_DER(2, Side.DER, Wheel.SIMPLE),
    E2_IZQ_EXT(2, Side.IZQ, Wheel.EXTERIOR), E2_IZQ_INT(2, Side.IZQ, Wheel.INTERIOR),
    E2_DER_INT(2, Side.DER, Wheel.INTERIOR), E2_DER_EXT(2, Side.DER, Wheel.EXTERIOR),
    E3_IZQ(3, Side.IZQ, Wheel.SIMPLE), E3_DER(3, Side.DER, Wheel.SIMPLE),
    E3_IZQ_EXT(3, Side.IZQ, Wheel.EXTERIOR), E3_IZQ_INT(3, Side.IZQ, Wheel.INTERIOR),
    E3_DER_INT(3, Side.DER, Wheel.INTERIOR), E3_DER_EXT(3, Side.DER, Wheel.EXTERIOR),
    AUXILIO(0, null, Wheel.SIMPLE);

    public enum Side { IZQ, DER }

    public enum Wheel { SIMPLE, EXTERIOR, INTERIOR }

    private final int axle;
    private final Side side;
    private final Wheel wheel;

    TirePosition(int axle, Side side, Wheel wheel) {
        this.axle = axle;
        this.side = side;
        this.wheel = wheel;
    }

    /** De izquierda a derecha mirando el vehículo desde arriba con el frente hacia adelante. */
    static List<TirePosition> ofAxle(int axle, boolean dual) {
        return Arrays.stream(values())
                .filter(p -> p.axle == axle && (p.wheel == Wheel.SIMPLE) != dual)
                .toList();
    }

    /** "e2-izq-ext", "e1-der", "auxilio". */
    public String toJson() {
        return name().toLowerCase().replace('_', '-');
    }

    public static TirePosition fromJson(String value) {
        return Arrays.stream(values()).filter(p -> p.toJson().equals(value)).findFirst().orElse(null);
    }

    public int axle() {
        return axle;
    }

    public Side side() {
        return side;
    }

    public Wheel wheel() {
        return wheel;
    }

    public boolean inner() {
        return wheel == Wheel.INTERIOR;
    }

    /** El auxilio no rueda: mientras está ahí no suma km. */
    public boolean rolls() {
        return this != AUXILIO;
    }
}
