package org.example.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pragmatic mapping for Vehicle.accessories: a comma-joined string column
 * instead of a Postgres text[] (which would need either an extra
 * hibernate-types dependency or a join table for plain Spring Data JPA).
 * Accessory codes are simple lowercase tokens (see checklist.AccessoryKey)
 * so they never contain commas -- fine for this MVP; revisit if that stops
 * being true.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return String.join(DELIMITER, attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        return List.of(dbData.split(DELIMITER)).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
