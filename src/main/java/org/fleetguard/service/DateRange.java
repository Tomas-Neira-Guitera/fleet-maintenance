package org.fleetguard.service;

import org.fleetguard.dto.FieldValidationErrorDetail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Período [from, to) de los listados filtrados por fecha (gastos CAM-52, actividad CAM-91). Cada
 * extremo acepta un instante ISO completo o una fecha sola (YYYY-MM-DD, en UTC); con fecha sola,
 * "to" incluye ese día entero. Sin extremo, el período queda abierto de ese lado.
 */
record DateRange(Instant from, Instant to) {

    private static final Instant OPEN_START = Instant.EPOCH;
    private static final Instant OPEN_END = Instant.parse("9999-01-01T00:00:00Z");

    /** Los valores inválidos se anotan en details y el extremo queda abierto: el que llama decide el error. */
    static DateRange parse(String from, String to, List<FieldValidationErrorDetail> details) {
        Instant start = parse(from, "from", false, details);
        Instant end = parse(to, "to", true, details);
        return new DateRange(start == null ? OPEN_START : start, end == null ? OPEN_END : end);
    }

    private static Instant parse(String value, String field, boolean endOfRange, List<FieldValidationErrorDetail> details) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException notAnInstant) {
            try {
                LocalDate date = LocalDate.parse(value);
                return (endOfRange ? date.plusDays(1) : date).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e) {
                details.add(new FieldValidationErrorDetail(field, "Fecha inválida, formato esperado ISO-8601"));
                return null;
            }
        }
    }
}
