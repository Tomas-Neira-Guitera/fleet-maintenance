package org.fleetguard.entity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catálogo de tipos de vehículo. El tipo define los ejes y sus cubiertas (CAM-157): ningún vehículo elige
 * sus posiciones aparte. Los remolques no tienen motor: suman los km de lo que los arrastra mientras están enganchados.
 */
public enum VehicleType {
    UTILITARIO("utilitario", "Utilitario", Kind.MOTOR, List.of(Wheels.SIMPLE, Wheels.SIMPLE)),
    CAMION_4X2("camion_4x2", "Camión rígido 4x2", Kind.RIGID, List.of(Wheels.SIMPLE, Wheels.DUAL)),
    CAMION_6X4("camion_6x4", "Camión rígido 6x2 / 6x4", Kind.RIGID, List.of(Wheels.SIMPLE, Wheels.DUAL, Wheels.DUAL)),
    TRACTOR_4X2("tractor_4x2", "Tractor 4x2", Kind.TRACTOR, List.of(Wheels.SIMPLE, Wheels.DUAL)),
    TRACTOR_6X4("tractor_6x4", "Tractor 6x4", Kind.TRACTOR, List.of(Wheels.SIMPLE, Wheels.DUAL, Wheels.DUAL)),
    SEMIRREMOLQUE_2_EJES("semirremolque_2_ejes", "Semirremolque 2 ejes", Kind.SEMITRAILER, List.of(Wheels.DUAL, Wheels.DUAL)),
    SEMIRREMOLQUE_3_EJES("semirremolque_3_ejes", "Semirremolque 3 ejes", Kind.SEMITRAILER,
            List.of(Wheels.DUAL, Wheels.DUAL, Wheels.DUAL)),
    ACOPLADO("acoplado", "Acoplado", Kind.FULL_TRAILER, List.of(Wheels.DUAL, Wheels.DUAL));

    /** MOTOR no arrastra nada; RIGID arrastra acoplados; TRACTOR, semirremolques. */
    public enum Kind { MOTOR, RIGID, TRACTOR, SEMITRAILER, FULL_TRAILER }

    public enum Wheels { SIMPLE, DUAL }

    // Lo que se suele escribir en una planilla o quedó de cuando el tipo era texto libre.
    private static final Map<String, VehicleType> ALIASES = Map.ofEntries(
            Map.entry("furgon", UTILITARIO), Map.entry("pickup", UTILITARIO), Map.entry("pick up", UTILITARIO),
            Map.entry("camioneta", UTILITARIO), Map.entry("van", UTILITARIO), Map.entry("utilitario", UTILITARIO),
            Map.entry("camion", CAMION_4X2), Map.entry("camion rigido", CAMION_4X2), Map.entry("camion 4x2", CAMION_4X2),
            Map.entry("camion 6x2", CAMION_6X4), Map.entry("camion 6x4", CAMION_6X4), Map.entry("balancin", CAMION_6X4),
            Map.entry("tractor", TRACTOR_4X2), Map.entry("semirremolque", SEMIRREMOLQUE_3_EJES),
            Map.entry("semi", SEMIRREMOLQUE_3_EJES), Map.entry("acoplado", ACOPLADO));

    private final String json;
    private final String label;
    private final Kind kind;
    private final List<Wheels> axles;

    VehicleType(String json, String label, Kind kind, List<Wheels> axles) {
        this.json = json;
        this.label = label;
        this.kind = kind;
        this.axles = axles;
    }

    /** Solo el código del contrato ("camion_4x2"). */
    public static VehicleType fromJson(String value) {
        return Arrays.stream(values()).filter(t -> t.json.equals(value)).findFirst().orElse(null);
    }

    /** Código, nombre o alias, sin importar mayúsculas ni acentos (importación y datos viejos). Null si no se reconoce. */
    public static VehicleType fromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        VehicleType byCode = fromJson(value.trim());
        if (byCode != null) {
            return byCode;
        }
        String plain = normalize(value);
        for (VehicleType type : values()) {
            if (normalize(type.label).equals(plain) || normalize(type.json).equals(plain)) {
                return type;
            }
        }
        return ALIASES.get(plain);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[_\\-/]+", " ").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static String toJson(VehicleType type) {
        return type == null ? null : type.json;
    }

    public String toJson() {
        return json;
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public List<Wheels> axles() {
        return axles;
    }

    public boolean isTrailer() {
        return kind == Kind.SEMITRAILER || kind == Kind.FULL_TRAILER;
    }

    /** Un tractor arrastra semirremolques; un camión rígido, acoplados. */
    public boolean canTow(VehicleType trailer) {
        return trailer != null && ((kind == Kind.TRACTOR && trailer.kind == Kind.SEMITRAILER)
                || (kind == Kind.RIGID && trailer.kind == Kind.FULL_TRAILER));
    }

    /** Las posiciones de sus cubiertas, eje por eje y de izquierda a derecha, con el auxilio al final. */
    public List<TirePosition> positions() {
        List<TirePosition> positions = new ArrayList<>();
        for (int i = 0; i < axles.size(); i++) {
            positions.addAll(TirePosition.ofAxle(i + 1, axles.get(i) == Wheels.DUAL));
        }
        positions.add(TirePosition.AUXILIO);
        return positions;
    }

    public boolean has(TirePosition position) {
        return positions().contains(position);
    }

    /** Un vehículo con motor dice "delantera" y "trasera"; con dos ejes traseros, o en un remolque, el número de eje. */
    public String labelOf(TirePosition position) {
        if (position == TirePosition.AUXILIO) {
            return "Auxilio";
        }
        String side = position.side() == TirePosition.Side.IZQ ? "izquierda" : "derecha";
        String axle;
        if (isTrailer() || (position.axle() > 1 && axles.size() > 2)) {
            axle = "Eje " + position.axle() + " " + side;
        } else {
            axle = (position.axle() == 1 ? "Delantera " : "Trasera ") + side;
        }
        return switch (position.wheel()) {
            case SIMPLE -> axle;
            case EXTERIOR -> axle + " exterior";
            case INTERIOR -> axle + " interior";
        };
    }
}
