package org.fleetguard.event;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.ScheduleSourceType;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.service.NotificationDispatcher;
import org.fleetguard.service.NotificationService;
import org.fleetguard.service.ScheduledToday;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-143: al admin, si un vehículo sale de viaje el día que tiene turno de taller (en hora argentina). */
class TripStartedWithMaintenanceListenerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private final ScheduledToday scheduledToday = mock(ScheduledToday.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TripStartedWithMaintenanceListener listener = new TripStartedWithMaintenanceListener(scheduledToday,
            notificationService, new NotificationDispatcher(mock(PlatformTransactionManager.class)));
    private final UUID vehicleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(scheduledToday.today()).thenReturn(TODAY);
        when(scheduledToday.firstOnDay(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void conTurnoEseDiaAvisaConChoferHorasYTitulo() {
        // Salió 12/11 a las 10:20 de Argentina; turno 15:00.
        Instant startedAt = Instant.parse("2026-11-12T13:20:00Z");
        when(scheduledToday.firstOnDay(vehicleId, TODAY)).thenReturn(Optional.of(
                new ScheduledMaintenance(vehicleId, ScheduleSourceType.MANUAL, null, null, "Cambio de aceite",
                        Instant.parse("2026-11-12T18:00:00Z"))));

        listener.onTripStarted(new TripStartedEvent(vehicleId, "AB123CD", "carlos.gomez", startedAt));

        verify(notificationService).notifyConfiguredRecipients(NotificationType.TRIP_STARTED_WITH_MAINTENANCE,
                "AB123CD salió de viaje con turno de taller",
                "carlos.gomez salió con AB123CD a las 10:20, y tiene turno de taller hoy a las 15:00: «Cambio de aceite».",
                "/vehiculos/" + vehicleId);
    }

    @Test
    void elDiaEsElArgentinoDeLaSalidaNoElDeUtc() {
        // 12/11 a las 22:30 de Argentina son las 01:30 del 13/11 en UTC: se busca el turno del 12.
        listener.onTripStarted(new TripStartedEvent(vehicleId, "AB123CD", "carlos.gomez",
                Instant.parse("2026-11-13T01:30:00Z")));

        verify(scheduledToday).firstOnDay(vehicleId, TODAY);
    }

    @Test
    void sinTurnoEseDiaNoAvisa() {
        listener.onTripStarted(new TripStartedEvent(vehicleId, "AB123CD", "carlos.gomez",
                Instant.parse("2026-11-12T13:20:00Z")));

        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any());
    }

    @Test
    void unPreViajeGuardadoSinSenalQueLlegaAlOtroDiaNoAvisa() {
        // CAM-153: salió el 11/11 y se mandó hoy. El aviso diría "hoy" sobre algo de ayer.
        listener.onTripStarted(new TripStartedEvent(vehicleId, "AB123CD", "carlos.gomez",
                Instant.parse("2026-11-11T13:20:00Z")));

        verify(scheduledToday, never()).firstOnDay(any(), any());
        verify(notificationService, never()).notifyConfiguredRecipients(any(), any(), any(), any());
    }
}
