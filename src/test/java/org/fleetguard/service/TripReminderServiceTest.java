package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-101 -- recordatorio al chofer de cerrar un viaje abierto hace más de N horas. */
class TripReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TripReminderService service = new TripReminderService(tripRepository, notificationService,
            new NotificationDispatcher(mock(PlatformTransactionManager.class)), Clock.fixed(NOW, ZoneOffset.UTC), 12);

    private final Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");

    /** El id es @GeneratedValue; en el test se setea por reflexión (mismo patrón que WorkOrderServiceTest). */
    private static Trip trip(Vehicle vehicle, String startedAt, UUID openedById) throws Exception {
        Trip trip = new Trip(vehicle, Instant.parse(startedAt), openedById, "Chofer");
        Field field = Trip.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(trip, UUID.randomUUID());
        return trip;
    }

    @BeforeEach
    void setUp() {
        when(tripRepository.findOpenTripsToRemind(any())).thenReturn(List.of());
    }

    @Test
    void buscaLosViajesAbiertosHaceMasDeLasHorasConfiguradas() {
        assertEquals(0, service.sendReminders());

        // El corte es "ahora - 12 h": un viaje de hace 13 horas entra y uno de hace 5 no.
        verify(tripRepository).findOpenTripsToRemind(Instant.parse("2026-10-23T03:00:00Z"));
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void leAvisaAlChoferQueAbrioElViajeYLoMarcaParaNoRepetir() throws Exception {
        UUID choferId = UUID.randomUUID();
        // 13 horas antes; 02:00 UTC son las 23:00 del día anterior en Argentina.
        Trip trip = trip(vehicle, "2026-10-23T02:00:00Z", choferId);
        when(tripRepository.findOpenTripsToRemind(any())).thenReturn(List.of(trip));

        assertEquals(1, service.sendReminders());

        verify(notificationService).notify(choferId, NotificationType.TRIP_OPEN_REMINDER,
                "Acordate de cerrar el viaje",
                "Tenés abierto el viaje de AB123CD desde el 22/10 a las 23:00. Acordate de hacer el post-viaje.", "/");
        // La consulta excluye los viajes con el recordatorio ya mandado: la corrida siguiente no lo repite.
        verify(tripRepository).markReminderSent(trip.getId(), NOW);
    }

    @Test
    void unAvisoQueFallaNoFrenaLosDemasYQuedaSinMarcarParaReintentar() throws Exception {
        UUID choferQueFalla = UUID.randomUUID();
        Trip falla = trip(vehicle, "2026-10-22T10:00:00Z", choferQueFalla);
        Trip anda = trip(vehicle, "2026-10-22T11:00:00Z", UUID.randomUUID());
        when(tripRepository.findOpenTripsToRemind(any())).thenReturn(List.of(falla, anda));
        doThrow(new IllegalStateException("base caída")).when(notificationService)
                .notify(eq(choferQueFalla), any(), anyString(), anyString(), any());

        assertEquals(1, service.sendReminders());

        verify(tripRepository, never()).markReminderSent(eq(falla.getId()), any());
        verify(tripRepository).markReminderSent(anda.getId(), NOW);
    }
}
