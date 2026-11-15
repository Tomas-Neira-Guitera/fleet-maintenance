package org.fleetguard.service;

import org.fleetguard.entity.ScheduleSourceType;
import org.fleetguard.entity.ScheduleStatus;
import org.fleetguard.entity.ScheduledMaintenance;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-143: el turno de hoy se busca en el día argentino (CAM-88), y solo entre los programados. */
class ScheduledTodayTest {

    private final ScheduledMaintenanceRepository repository = mock(ScheduledMaintenanceRepository.class);

    static ScheduledMaintenance schedule(UUID vehicleId, String title, Instant at) throws Exception {
        ScheduledMaintenance schedule = new ScheduledMaintenance(vehicleId, ScheduleSourceType.MANUAL, null, null, title, at);
        Field id = ScheduledMaintenance.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(schedule, UUID.randomUUID());
        return schedule;
    }

    private ScheduledToday at(String instant) {
        return new ScheduledToday(repository, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void aLas23DeArgentinaHoySigueSiendoEseDiaAunqueEnUtcYaSeaElSiguiente() {
        // 12/11 23:30 en Argentina son las 02:30 del 13/11 en UTC.
        ScheduledToday today = at("2026-11-13T02:30:00Z");

        assertEquals(LocalDate.of(2026, 11, 12), today.today());
    }

    @Test
    @SuppressWarnings("unchecked")
    void elDiaVaDeLas0A23_59DeArgentinaYSoloConTurnosProgramados() {
        ScheduledToday today = at("2026-11-12T15:00:00Z");
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Collection<ScheduleStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        when(repository.findByScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(any(), any(), any())).thenReturn(List.of());

        today.forAll();

        verify(repository).findByScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(from.capture(), to.capture(),
                statuses.capture());
        assertEquals(Instant.parse("2026-11-12T03:00:00Z"), from.getValue());
        assertEquals(Instant.parse("2026-11-13T02:59:59.999999Z"), to.getValue());
        // Un turno cancelado o hecho no se muestra.
        assertEquals(List.of(ScheduleStatus.SCHEDULED), List.copyOf(statuses.getValue()));
        // Un turno a las 22:00 de Argentina (01:00 UTC del día siguiente) entra en el día.
        Instant at22 = Instant.parse("2026-11-13T01:00:00Z");
        assertTrue(!at22.isBefore(from.getValue()) && !at22.isAfter(to.getValue()));
        // Y uno a las 00:00 del día siguiente no.
        assertTrue(Instant.parse("2026-11-13T03:00:00Z").isAfter(to.getValue()));
    }

    @Test
    void deCadaVehiculoQuedaElTurnoMasTemprano() throws Exception {
        UUID truck = UUID.randomUUID();
        UUID van = UUID.randomUUID();
        Instant morning = Instant.parse("2026-11-12T12:00:00Z");
        Instant afternoon = Instant.parse("2026-11-12T18:00:00Z");
        // El repositorio los devuelve ordenados por hora.
        when(repository.findByScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(any(), any(), any())).thenReturn(List.of(
                schedule(truck, "Aceite", morning), schedule(van, "Frenos", morning), schedule(truck, "Luces", afternoon)));

        Map<UUID, Instant> result = at("2026-11-12T15:00:00Z").forAll();

        assertEquals(Map.of(truck, morning, van, morning), result);
    }

    @Test
    void unVehiculoSinTurnoHoyDaNull() {
        UUID vehicleId = UUID.randomUUID();
        when(repository.findByVehicleIdAndScheduledAtBetweenAndStatusInOrderByScheduledAtAsc(eq(vehicleId), any(), any(),
                any())).thenReturn(List.of());

        ScheduledToday today = at("2026-11-12T15:00:00Z");
        assertNull(today.forVehicle(vehicleId));
        assertFalse(today.firstOnDay(vehicleId, LocalDate.of(2026, 11, 12)).isPresent());
    }
}
