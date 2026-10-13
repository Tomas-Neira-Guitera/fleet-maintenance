package org.fleetguard.service;

import org.fleetguard.entity.Vehicle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAM-90: cuándo una lectura de km avanza el odómetro del vehículo. */
class OdometerReadingsTest {

    private final OdometerReadings readings = new OdometerReadings(5000);

    private static Vehicle vehicleWith(long km) {
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        vehicle.setOdometerKm(km);
        return vehicle;
    }

    @Test
    void unaLecturaMayorAvanzaElOdometro() {
        Vehicle vehicle = vehicleWith(120000);

        assertTrue(readings.apply(vehicle, 120350));
        assertEquals(120350, vehicle.getOdometerKm());
    }

    @Test
    void unaLecturaMenorOIgualNoLoCambia() {
        Vehicle vehicle = vehicleWith(120000);

        assertFalse(readings.apply(vehicle, 119000));
        assertFalse(readings.apply(vehicle, 120000));
        assertEquals(120000, vehicle.getOdometerKm());
    }

    @Test
    void unSaltoHastaElTopeAvanzaYUnoMayorNo() {
        Vehicle vehicle = vehicleWith(120000);

        // Un error de tipeo (1.200.000 en vez de 120.000) no queda cargado para siempre.
        assertFalse(readings.apply(vehicle, 1_200_000));
        assertFalse(readings.apply(vehicle, 125001));
        assertEquals(120000, vehicle.getOdometerKm());

        assertTrue(readings.apply(vehicle, 125000));
        assertEquals(125000, vehicle.getOdometerKm());
    }
}
