package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.entity.ScheduledMaintenanceAlert;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.TripStatus;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.repository.ScheduledMaintenanceAlertRepository;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.fleetguard.service.ScheduledTodayTest.schedule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-143: la corrida diaria le avisa al chofer que tiene el vehículo en viaje de los turnos de hoy y de
 * mañana, una sola vez por fecha. El registro de avisados se simula en memoria.
 */
class ScheduledMaintenanceAlertServiceTest {

    // 12/11 a las 08:15 de Argentina.
    static final Instant NOW = Instant.parse("2026-11-12T11:15:00Z");
    private static final Instant TOMORROW_15 = Instant.parse("2026-11-13T18:00:00Z");
    private static final Instant TODAY_15 = Instant.parse("2026-11-12T18:00:00Z");

    private final ScheduledMaintenanceRepository scheduleRepository = mock(ScheduledMaintenanceRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ScheduledMaintenanceAlertRepository alertRepository = mock(ScheduledMaintenanceAlertRepository.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ScheduledMaintenanceAlertService service = new ScheduledMaintenanceAlertService(
            new ScheduledToday(scheduleRepository, clock), tripRepository, alertRepository, settings,
            notificationService, new NotificationDispatcher(mock(PlatformTransactionManager.class)), clock);

    private final List<ScheduledMaintenanceAlert> alerted = new ArrayList<>();
    private final List<ScheduledMaintenance> scheduled = new ArrayList<>();
    private final List<Trip> openTrips = new ArrayList<>();
    private Vehicle truck;
    private final UUID driver = UUID.randomUUID();

    private static Vehicle vehicle(String plate) throws Exception {
        Vehicle vehicle = new Vehicle(plate, "Iveco", "Daily");
        Field id = Vehicle.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(vehicle, UUID.randomUUID());
        return vehicle;
    }

    private void enabled(boolean on) {
        when(settings.effective(NotificationType.SCHEDULED_MAINTENANCE_UPCOMING)).thenReturn(
                new EffectiveNotificationSetting(NotificationType.SCHEDULED_MAINTENANCE_UPCOMING, on,
                        Set.of(NotificationChannel.IN_APP), Set.of(), Set.of(), null, null, false));
    }

    @BeforeEach
    void setUp() throws Exception {
        truck = vehicle("AB123CD");
        enabled(true);
        // El repositorio real filtra por rango; acá se simula con la lista en memoria.
        when(scheduleRepository.findByScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Instant from = invocation.getArgument(0);
                    Instant to = invocation.getArgument(1);
                    return scheduled.stream()
                            .filter(s -> !s.getScheduledAt().isBefore(from) && !s.getScheduledAt().isAfter(to)).toList();
                });
        when(tripRepository.findByStatusWithVehicle(TripStatus.OPEN)).thenAnswer(invocation -> openTrips);
        when(alertRepository.existsByScheduleIdAndScheduledAt(any(), any())).thenAnswer(invocation -> alerted.stream()
                .anyMatch(a -> a.getScheduleId().equals(invocation.getArgument(0))
                        && a.getScheduledAt().equals(invocation.getArgument(1))));
        when(alertRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ScheduledMaintenanceAlert alert = invocation.getArgument(0);
            alerted.add(alert);
            return alert;
        });
    }

    private Trip onTrip(Vehicle vehicle, UUID driverId) {
        Trip trip = new Trip(vehicle, NOW.minusSeconds(3600), driverId, "carlos.gomez");
        openTrips.add(trip);
        return trip;
    }

    @Test
    void unVehiculoEnViajeConTurnoMananaLeAvisaAlChoferConVehiculoFechaYHora() throws Exception {
        onTrip(truck, driver);
        scheduled.add(schedule(truck.getId(), "Cambio de aceite", TOMORROW_15));

        assertEquals(1, service.checkAll());

        verify(notificationService).notify(driver, NotificationType.SCHEDULED_MAINTENANCE_UPCOMING,
                "Turno de taller de AB123CD",
                "AB123CD tiene turno de taller mañana 13/11 a las 15:00: «Cambio de aceite». "
                        + "Tenelo en cuenta para volver a tiempo.",
                "/");
    }

    @Test
    void unTurnoDeHoyQueTodaviaNoPasoTambienAvisa() throws Exception {
        onTrip(truck, driver);
        scheduled.add(schedule(truck.getId(), "Frenos", TODAY_15));

        assertEquals(1, service.checkAll());

        verify(notificationService).notify(eq(driver), eq(NotificationType.SCHEDULED_MAINTENANCE_UPCOMING), anyString(),
                eq("AB123CD tiene turno de taller hoy a las 15:00: «Frenos». Tenelo en cuenta para volver a tiempo."),
                eq("/"));
    }

    @Test
    void unTurnoDeHoyQueYaPasoNoAvisa() throws Exception {
        onTrip(truck, driver);
        scheduled.add(schedule(truck.getId(), "Frenos", Instant.parse("2026-11-12T10:00:00Z")));

        assertEquals(0, service.checkAll());
    }

    @Test
    void unTurnoDePasadoMananaTodaviaNoAvisa() throws Exception {
        onTrip(truck, driver);
        scheduled.add(schedule(truck.getId(), "Frenos", Instant.parse("2026-11-14T18:00:00Z")));

        assertEquals(0, service.checkAll());
    }

    @Test
    void unVehiculoSinViajeAbiertoNoGeneraAviso() throws Exception {
        scheduled.add(schedule(truck.getId(), "Cambio de aceite", TOMORROW_15));

        assertEquals(0, service.checkAll());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
        assertTrue(alerted.isEmpty());
    }

    @Test
    void unViajeSinChoferCargadoNoAvisa() throws Exception {
        onTrip(truck, null);
        scheduled.add(schedule(truck.getId(), "Cambio de aceite", TOMORROW_15));

        assertEquals(0, service.checkAll());
    }

    @Test
    void elMismoTurnoSeAvisaUnaSolaVezAunqueCorraDeNuevo() throws Exception {
        onTrip(truck, driver);
        scheduled.add(schedule(truck.getId(), "Cambio de aceite", TOMORROW_15));

        assertEquals(1, service.checkAll());
        assertEquals(0, service.checkAll());

        verify(notificationService).notify(any(), any(), any(), any(), any());
    }

    @Test
    void siSeReprogramaSeAvisaDeNuevo() throws Exception {
        onTrip(truck, driver);
        ScheduledMaintenance schedule = schedule(truck.getId(), "Cambio de aceite", TODAY_15);
        scheduled.add(schedule);
        assertEquals(1, service.checkAll());

        schedule.reschedule(TOMORROW_15);

        assertEquals(1, service.checkAll());
        assertEquals(2, alerted.size());
    }

    @Test
    void conElAvisoApagadoNoSeAnotaNadaYAlPrenderloSeAvisa() throws Exception {
        onTrip(truck, driver);
        scheduled.add(schedule(truck.getId(), "Cambio de aceite", TOMORROW_15));
        enabled(false);

        assertEquals(0, service.checkAll());
        assertTrue(alerted.isEmpty());

        enabled(true);
        assertEquals(1, service.checkAll());
    }

    @Test
    void unVehiculoDadoDeBajaNoAvisa() throws Exception {
        truck.setActive(false);
        onTrip(truck, driver);
        scheduled.add(schedule(truck.getId(), "Cambio de aceite", TOMORROW_15));

        assertEquals(0, service.checkAll());
    }
}
