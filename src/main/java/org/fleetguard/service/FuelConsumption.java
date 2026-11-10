package org.fleetguard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CAM-156: consumo por el método de tanque lleno. Un tramo va de una carga de tanque lleno a la
 * siguiente: los litros son los de todas las cargas después de la primera (las parciales del medio y la
 * que cierra) y los km, la diferencia de odómetro. Las parciales antes del primer tanque lleno no cuentan.
 */
final class FuelConsumption {

    private FuelConsumption() {
    }

    record Load(UUID id, Instant at, long km, BigDecimal liters, BigDecimal amount, boolean fullTank) {
    }

    record Segment(UUID closingLoadId, Instant closedAt, long km, BigDecimal liters, BigDecimal amount) {

        BigDecimal litersPer100Km() {
            return per100Km(liters, km);
        }

        BigDecimal costPerKm() {
            return perKm(amount, km);
        }
    }

    /** loads en el orden en que se hicieron. */
    static List<Segment> segments(List<Load> loads) {
        List<Segment> segments = new ArrayList<>();
        Load lastFull = null;
        BigDecimal liters = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (Load load : loads) {
            if (lastFull == null) {
                if (load.fullTank()) {
                    lastFull = load;
                }
                continue;
            }
            liters = liters.add(load.liters());
            amount = amount.add(load.amount());
            if (!load.fullTank()) {
                continue;
            }
            long km = load.km() - lastFull.km();
            // Llenar dos veces en el mismo km no es un tramo: el tanque arranca lleno desde la última.
            if (km > 0) {
                segments.add(new Segment(load.id(), load.at(), km, liters, amount));
            }
            lastFull = load;
            liters = BigDecimal.ZERO;
            amount = BigDecimal.ZERO;
        }
        return segments;
    }

    /** Consumo de varios tramos juntos: litros totales sobre km totales, no el promedio de cada uno. */
    static BigDecimal litersPer100Km(List<Segment> segments) {
        return per100Km(segments.stream().map(Segment::liters).reduce(BigDecimal.ZERO, BigDecimal::add),
                segments.stream().mapToLong(Segment::km).sum());
    }

    static BigDecimal costPerKm(List<Segment> segments) {
        return perKm(segments.stream().map(Segment::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                segments.stream().mapToLong(Segment::km).sum());
    }

    static BigDecimal per100Km(BigDecimal liters, long km) {
        return km <= 0 ? null : liters.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal perKm(BigDecimal amount, long km) {
        return km <= 0 ? null : amount.divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP);
    }
}
