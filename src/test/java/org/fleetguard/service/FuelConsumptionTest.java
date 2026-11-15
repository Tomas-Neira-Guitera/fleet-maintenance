package org.fleetguard.service;

import org.fleetguard.service.FuelConsumption.Load;
import org.fleetguard.service.FuelConsumption.Segment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// CAM-156: consumo por el método de tanque lleno.
class FuelConsumptionTest {

    private static Load load(int day, long km, String liters, boolean full) {
        return new Load(UUID.randomUUID(), Instant.parse("2026-11-01T10:00:00Z").plusSeconds(86_400L * day), km,
                new BigDecimal(liters), new BigDecimal(liters).multiply(BigDecimal.TEN), full);
    }

    @Test
    void lasParcialesAntesDelPrimerTanqueLlenoNoCuentan() {
        List<Segment> segments = FuelConsumption.segments(List.of(
                load(0, 1000, "30", false), load(1, 1200, "80", true), load(2, 1700, "100", true)));

        assertEquals(1, segments.size());
        assertEquals(500, segments.get(0).km());
        assertEquals(new BigDecimal("20.00"), segments.get(0).litersPer100Km());
        assertEquals(new BigDecimal("2.00"), segments.get(0).costPerKm());
    }

    @Test
    void llenarDosVecesEnElMismoKmNoEsUnTramo() {
        List<Segment> segments = FuelConsumption.segments(List.of(
                load(0, 1000, "80", true), load(1, 1000, "5", true), load(2, 1500, "100", true)));

        assertEquals(1, segments.size());
        assertEquals(new BigDecimal("100"), segments.get(0).liters());
    }

    @Test
    void elConsumoDeVariosTramosEsLitrosTotalesSobreKmTotales() {
        List<Segment> segments = FuelConsumption.segments(List.of(
                load(0, 1000, "80", true), load(1, 1100, "30", true), load(2, 1500, "80", true)));

        // 110 L en 500 km, no el promedio de 30 y 20.
        assertEquals(new BigDecimal("22.00"), FuelConsumption.litersPer100Km(segments));
        assertNull(FuelConsumption.litersPer100Km(List.of()));
        assertTrue(FuelConsumption.segments(List.of(load(0, 1000, "80", false))).isEmpty());
    }
}
