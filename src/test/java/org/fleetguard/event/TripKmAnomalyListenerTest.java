package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.TripKmAlertReason;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** CAM-152: el aviso dice el vehículo, el chofer, los km del viaje y el promedio, y lleva al vehículo. */
class TripKmAnomalyListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final TripKmAnomalyListener listener = new TripKmAnomalyListener(notificationService,
            new NotificationDispatcher(mock(PlatformTransactionManager.class)));
    private final UUID vehicleId = UUID.randomUUID();

    @Test
    void unViajeDeMasDelDoble() {
        listener.onTripKmAnomaly(new TripKmAnomalyEvent(vehicleId, "AB123CD", "chofer1",
                TripKmAlertReason.ABOVE_AVERAGE, 1250L, 400L, 51250, 50000));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.TRIP_KM_ANOMALY,
                "Kilometraje fuera de lo normal en AB123CD",
                "chofer1 cerró un viaje de 1.250 km con AB123CD, más del doble de su promedio de 400 km por viaje.",
                "/vehiculos/" + vehicleId);
    }

    @Test
    void unViajeDeMenosDeLaMitad() {
        listener.onTripKmAnomaly(new TripKmAnomalyEvent(vehicleId, "AB123CD", "chofer1",
                TripKmAlertReason.BELOW_AVERAGE, 50L, 400L, 50050, 50000));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.TRIP_KM_ANOMALY,
                "Kilometraje fuera de lo normal en AB123CD",
                "chofer1 cerró un viaje de 50 km con AB123CD, menos de la mitad de su promedio de 400 km por viaje.",
                "/vehiculos/" + vehicleId);
    }

    @Test
    void unKmMenorAlDelVehiculoSinChofer() {
        listener.onTripKmAnomaly(new TripKmAnomalyEvent(vehicleId, "AB123CD", null,
                TripKmAlertReason.BELOW_VEHICLE_KM, null, null, 49000, 50000));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.TRIP_KM_ANOMALY,
                "Kilometraje fuera de lo normal en AB123CD",
                "El chofer cerró el viaje de AB123CD con 49.000 km, menos de los 50.000 km que ya tenía el vehículo.",
                "/vehiculos/" + vehicleId);
    }

    @Test
    void unErrorAlAvisarNoSePropaga() {
        doThrow(new IllegalStateException("caído")).when(notificationService)
                .notifyConfiguredRecipients(any(), any(), any(), any());

        listener.onTripKmAnomaly(new TripKmAnomalyEvent(vehicleId, "AB123CD", "c",
                TripKmAlertReason.ABOVE_AVERAGE, 900L, 300L, 50900, 50000));
    }
}
