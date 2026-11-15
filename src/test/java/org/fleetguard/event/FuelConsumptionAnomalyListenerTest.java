package org.fleetguard.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// CAM-156: el texto del aviso de consumo anormal.
class FuelConsumptionAnomalyListenerTest {

    @Test
    void elAvisoDiceCuantoConsumioYContraQuePromedio() {
        FuelConsumptionAnomalyEvent event = new FuelConsumptionAnomalyEvent(UUID.randomUUID(), "AB123CD",
                new BigDecimal("45.00"), new BigDecimal("30.00"), 1200);

        assertEquals("AB123CD consumió 45,0 L cada 100 km en los últimos 1.200 km, un 50% más que su promedio de 30,0 L.",
                FuelConsumptionAnomalyListener.message(event));
    }
}
