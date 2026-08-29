package org.example.checklist;

import java.util.Arrays;
import java.util.Optional;

/** Mirrors the frontend's AccessoryKey union type. */
public enum AccessoryKey {
    FAJA("faja"),
    TRACA("traca"),
    GRUA("grua"),
    RAMPA("rampa");

    private final String code;

    AccessoryKey(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<AccessoryKey> fromCode(String code) {
        return Arrays.stream(values()).filter(a -> a.code.equals(code)).findFirst();
    }
}
