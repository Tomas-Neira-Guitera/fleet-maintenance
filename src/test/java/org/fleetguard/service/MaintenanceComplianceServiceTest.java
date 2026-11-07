package org.fleetguard.service;

import org.fleetguard.dto.MaintenanceComplianceDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.exception.WorkOrderValidationException;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-155 -- indicadores de cumplimiento del mantenimiento. */
class MaintenanceComplianceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");

    private final MaintenanceCompletionRepository completionRepository = mock(MaintenanceCompletionRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final MaintenanceComplianceService service = new MaintenanceComplianceService(completionRepository,
            defectRepository, workOrderRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    private static void set(Object entity, String field, Object value) throws Exception {
        Field f = entity.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(entity, value);
    }

    private static VehicleMaintenanceAssignment assignment(MaintenancePlan plan) throws Exception {
        VehicleMaintenanceAssignment assignment = new VehicleMaintenanceAssignment(UUID.randomUUID(), plan, null, null);
        set(assignment, "id", UUID.randomUUID());
        return assignment;
    }

    private static MaintenanceCompletion done(VehicleMaintenanceAssignment assignment, String date, Long km) {
        return new MaintenanceCompletion(assignment, LocalDate.parse(date), km, null, null);
    }

    @BeforeEach
    void setUp() {
        when(completionRepository.findCompletedBeforeWithPlan(any())).thenReturn(List.of());
        when(defectRepository.findResolvedBetween(any(), any())).thenReturn(List.of());
        when(workOrderRepository.findByStatusIn(any())).thenReturn(List.of());
    }

    @Test
    void cadaMantenimientoDelPeriodoSeComparaConElAnteriorMasElIntervalo() throws Exception {
        MaintenancePlan everySixMonths = new MaintenancePlan("RTO", "documentación", IntervalType.TIME, null, 180);
        MaintenancePlan everyTenThousandKm = new MaintenancePlan("Aceite", "motor", IntervalType.KM, 10000, null);
        VehicleMaintenanceAssignment rto = assignment(everySixMonths);
        VehicleMaintenanceAssignment oil = assignment(everyTenThousandKm);
        when(completionRepository.findCompletedBeforeWithPlan(LocalDate.parse("2026-11-01"))).thenReturn(List.of(
                // RTO: a tiempo (vencía el 2026-10-15) y la primera vez no cuenta.
                done(rto, "2026-04-18", null), done(rto, "2026-10-10", null),
                // Aceite: el de septiembre queda fuera del período; el de octubre, tarde (vencía a los 61.000 km).
                done(oil, "2026-05-01", 41000L), done(oil, "2026-10-20", 61500L), done(oil, "2026-09-01", 51000L)));

        MaintenanceComplianceDto result = service.get("2026-10-01", "2026-10-31");

        assertEquals(1, result.preventive().onTime());
        assertEquals(1, result.preventive().late());
        assertEquals(50.0, result.preventive().onTimePct());
        verify(completionRepository).findCompletedBeforeWithPlan(LocalDate.parse("2026-11-01"));
    }

    @Test
    void conKmYTiempoHayQueCumplirLosDos() throws Exception {
        MaintenancePlan both = new MaintenancePlan("Service", "motor", IntervalType.BOTH, 10000, 365);
        VehicleMaintenanceAssignment assignment = assignment(both);
        MaintenanceCompletion previous = done(assignment, "2026-01-10", 100000L);

        assertEquals(true, MaintenanceComplianceService.onTime(previous, done(assignment, "2026-06-01", 109000L)));
        assertEquals(false, MaintenanceComplianceService.onTime(previous, done(assignment, "2026-06-01", 112000L)));
        assertEquals(false, MaintenanceComplianceService.onTime(previous, done(assignment, "2027-02-01", 105000L)));
        // Sin los km de alguno de los dos, decide solo la fecha.
        assertEquals(true, MaintenanceComplianceService.onTime(previous, done(assignment, "2026-06-01", null)));
    }

    @Test
    void sinMantenimientosEnElPeriodoElPorcentajeEsNull() {
        assertNull(service.get("2026-10-01", "2026-10-31").preventive().onTimePct());
    }

    @Test
    void elTiempoDeResolucionEsElPromedioDeLosDefectosResueltosEnElPeriodo() {
        Defect fast = new Defect(DefectSeverity.BLOCKING, "Frenos", null, Instant.parse("2026-10-05T10:00:00Z"));
        fast.resolve(Instant.parse("2026-10-05T16:00:00Z"));
        Defect slow = new Defect(DefectSeverity.NON_BLOCKING, "Farol", null, Instant.parse("2026-10-01T10:00:00Z"));
        slow.resolve(Instant.parse("2026-10-02T16:00:00Z"));
        when(defectRepository.findResolvedBetween(Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"))).thenReturn(List.of(fast, slow));

        MaintenanceComplianceDto.DefectResolution result = service.get("2026-10-01", "2026-10-31").defects();

        assertEquals(2, result.resolved());
        assertEquals(18.0, result.avgHours());
    }

    @Test
    void lasOtsAbiertasSeSeparanPorAntiguedad() throws Exception {
        List<WorkOrder> open = List.of(workOrder(2), workOrder(6), workOrder(7), workOrder(30), workOrder(45));
        when(workOrderRepository.findByStatusIn(any())).thenReturn(open);

        MaintenanceComplianceDto.OpenWorkOrders result = service.get("2026-10-01", "2026-10-31").openWorkOrders();

        assertEquals(5, result.total());
        assertEquals(2, result.under7Days());
        assertEquals(2, result.from7To30Days());
        assertEquals(1, result.over30Days());
    }

    @Test
    void sinPeriodoDevuelve422() {
        assertThrows(WorkOrderValidationException.class, () -> service.get(null, "2026-10-31"));
        assertThrows(WorkOrderValidationException.class, () -> service.get("2026-10-31", "2026-10-01"));
    }

    private static WorkOrder workOrder(int daysOld) throws Exception {
        WorkOrder workOrder = new WorkOrder(UUID.randomUUID(), WorkOrderSourceType.MANUAL, null, null, null,
                "Reparación", null, WorkOrderExecutionType.INTERNO, null, null, null);
        set(workOrder, "createdAt", NOW.minus(Duration.ofDays(daysOld)));
        return workOrder;
    }
}
