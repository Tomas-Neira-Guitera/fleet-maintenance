package org.fleetguard.service;

import org.fleetguard.entity.CheckOutcome;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.ScheduleStatus;
import org.fleetguard.entity.WorkOrderStatus;
import org.fleetguard.repository.ScheduledMaintenanceRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAM-142: un defecto está sin atender si sigue abierto, lleva más de 7 días desde que se reportó y no tiene
 * una OT abierta ni un turno programado. "Hoy" es el 12/11 a las 09:00 de Argentina.
 */
class UnattendedDefectsTest {

    static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final ScheduledMaintenanceRepository scheduleRepository = mock(ScheduledMaintenanceRepository.class);
    private final UnattendedDefects unattended = new UnattendedDefects(workOrderRepository, scheduleRepository,
            Clock.fixed(NOW, ZoneOffset.UTC), 7);

    static Defect defect(UUID vehicleId, String description, Instant reportedAt) throws Exception {
        Inspection inspection = new Inspection(null, vehicleId, UUID.randomUUID().toString(), "carlos.gomez",
                InspectionType.PRE_TRIP, reportedAt, null, null, false);
        InspectionAnswer answer = new InspectionAnswer("item-" + description, description, CheckOutcome.DEFECT, null);
        inspection.addAnswer(answer);
        Defect defect = new Defect(DefectSeverity.NON_BLOCKING, description, null, reportedAt);
        answer.attachDefect(defect);
        Field id = Defect.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(defect, UUID.randomUUID());
        return defect;
    }

    /** Reportado el 12/11 menos esos días, a las 10:00 de Argentina. */
    static Instant daysAgo(int days) {
        return Instant.parse("2026-11-12T13:00:00Z").minusSeconds(days * 86400L);
    }

    @Test
    void unDefectoAbiertoHace8DiasSinOtNiTurnoEstaSinAtender() throws Exception {
        Defect defect = defect(UUID.randomUUID(), "Luz de freno", daysAgo(8));

        assertEquals(Map.of(defect.getId(), 8L), unattended.unattendedDays(List.of(defect)));
    }

    @Test
    void conSieteDiasTodaviaNoCuentaAunqueHayaSidoMasTempranoEnElDia() throws Exception {
        // Reportado el 05/11 a las 00:30 de Argentina: son 7 días de calendario, no 8.
        Defect defect = defect(UUID.randomUUID(), "Espejo", Instant.parse("2026-11-05T03:30:00Z"));

        assertTrue(unattended.unattendedDays(List.of(defect)).isEmpty());
        // Sin candidatos no se consulta nada.
        verify(workOrderRepository, never()).findDefectIdsWithStatusIn(any());
    }

    @Test
    void losDiasSeCuentanEnArgentinaNoEnUtc() throws Exception {
        // 05/11 01:00 UTC es el 04/11 a las 22:00 de Argentina: son 8 días (en UTC serían 7).
        Defect defect = defect(UUID.randomUUID(), "Bocina", Instant.parse("2026-11-05T01:00:00Z"));

        assertEquals(Map.of(defect.getId(), 8L), unattended.unattendedDays(List.of(defect)));
    }

    @Test
    void unaOtAbiertaOEnEsperaOUnTurnoProgramadoLoAtienden() throws Exception {
        Defect withWorkOrder = defect(UUID.randomUUID(), "Luz de freno", daysAgo(10));
        Defect withSchedule = defect(UUID.randomUUID(), "Espejo", daysAgo(10));
        Defect alone = defect(UUID.randomUUID(), "Bocina", daysAgo(10));
        when(workOrderRepository.findDefectIdsWithStatusIn(WorkOrderStatus.OPEN)).thenReturn(List.of(withWorkOrder.getId()));
        when(scheduleRepository.findDefectIdsWithStatus(ScheduleStatus.SCHEDULED)).thenReturn(List.of(withSchedule.getId()));

        assertEquals(Map.of(alone.getId(), 10L), unattended.unattendedDays(List.of(withWorkOrder, withSchedule, alone)));
        // En espera (CAM-148) es una OT abierta: cuenta como atención.
        assertTrue(WorkOrderStatus.OPEN.contains(WorkOrderStatus.EN_ESPERA));
    }

    @Test
    void soloSePidenLasOtsAbiertasYLosTurnosProgramados() throws Exception {
        // Una OT o un turno cancelados no vuelven en esas consultas, así que no cuentan como atención. Las
        // consultas en sí no tienen test de JPA, como el resto del repo.
        Defect defect = defect(UUID.randomUUID(), "Luz de freno", daysAgo(9));
        when(workOrderRepository.findDefectIdsWithStatusIn(WorkOrderStatus.OPEN)).thenReturn(List.of());
        when(scheduleRepository.findDefectIdsWithStatus(ScheduleStatus.SCHEDULED)).thenReturn(List.of());

        assertEquals(Map.of(defect.getId(), 9L), unattended.unattendedDays(List.of(defect)));
        verify(workOrderRepository).findDefectIdsWithStatusIn(List.of(WorkOrderStatus.ASIGNADA, WorkOrderStatus.EN_PROCESO,
                WorkOrderStatus.EN_ESPERA));
        verify(scheduleRepository).findDefectIdsWithStatus(ScheduleStatus.SCHEDULED);
    }

    @Test
    void unDefectoResueltoNoEstaSinAtender() throws Exception {
        Defect defect = defect(UUID.randomUUID(), "Luz de freno", daysAgo(30));
        defect.resolve(NOW);

        assertTrue(unattended.unattendedDays(List.of(defect)).isEmpty());
    }

    @Test
    void sonDosConsultasEnTotalSinImportarCuantosDefectosSean() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        List<Defect> defects = List.of(defect(vehicleId, "A", daysAgo(8)), defect(vehicleId, "B", daysAgo(9)),
                defect(vehicleId, "C", daysAgo(20)));

        assertEquals(3, unattended.unattendedDays(defects).size());
        verify(workOrderRepository).findDefectIdsWithStatusIn(WorkOrderStatus.OPEN);
        verify(scheduleRepository).findDefectIdsWithStatus(ScheduleStatus.SCHEDULED);
    }
}
