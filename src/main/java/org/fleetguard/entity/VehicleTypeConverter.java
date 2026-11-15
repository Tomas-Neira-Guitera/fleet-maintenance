package org.fleetguard.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// La columna guarda el código ("camion_4x2"). Un valor de cuando el tipo era texto libre se lee con sus alias;
// uno que no se reconoce queda sin tipo, para que el admin lo elija.
@Converter
public class VehicleTypeConverter implements AttributeConverter<VehicleType, String> {

    @Override
    public String convertToDatabaseColumn(VehicleType type) {
        return VehicleType.toJson(type);
    }

    @Override
    public VehicleType convertToEntityAttribute(String value) {
        return VehicleType.fromText(value);
    }
}
