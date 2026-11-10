package org.fleetguard.entity;

import java.util.Arrays;
import java.util.List;

import static org.fleetguard.entity.TirePosition.*;

/** CAM-157: configuración de ejes del vehículo. De ella salen sus posiciones, siempre con auxilio. */
public enum TireLayout {
    CUATRO_RUEDAS("4_ruedas", "4 ruedas", List.of(DEL_IZQ, DEL_DER, TRAS_IZQ, TRAS_DER, AUXILIO)),
    SEIS_RUEDAS("6_ruedas", "6 ruedas (trasero dual)", List.of(DEL_IZQ, DEL_DER,
            E2_IZQ_EXT, E2_IZQ_INT, E2_DER_INT, E2_DER_EXT, AUXILIO)),
    DIEZ_RUEDAS("10_ruedas", "10 ruedas (dos ejes traseros duales)", List.of(DEL_IZQ, DEL_DER,
            E2_IZQ_EXT, E2_IZQ_INT, E2_DER_INT, E2_DER_EXT, E3_IZQ_EXT, E3_IZQ_INT, E3_DER_INT, E3_DER_EXT, AUXILIO));

    private final String json;
    private final String label;
    private final List<TirePosition> positions;

    TireLayout(String json, String label, List<TirePosition> positions) {
        this.json = json;
        this.label = label;
        this.positions = positions;
    }

    public static TireLayout fromJson(String value) {
        return Arrays.stream(values()).filter(l -> l.json.equals(value)).findFirst().orElse(null);
    }

    public String toJson() {
        return json;
    }

    public String label() {
        return label;
    }

    public List<TirePosition> positions() {
        return positions;
    }

    public boolean has(TirePosition position) {
        return positions.contains(position);
    }

    /** Con un solo eje trasero se dice "trasera"; con dos, el número de eje. */
    public String labelOf(TirePosition position) {
        if (position == AUXILIO) {
            return "Auxilio";
        }
        String side = position.side() == TirePosition.Side.IZQ ? "izquierda" : "derecha";
        if (position.axle() == 1) {
            return "Delantera " + side;
        }
        String where = this == DIEZ_RUEDAS ? "Eje " + position.axle() + " " + side : "Trasera " + side;
        boolean dual = this != CUATRO_RUEDAS;
        return dual ? where + (position.inner() ? " interior" : " exterior") : where;
    }
}
