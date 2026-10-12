package org.fleetguard.service;

import org.fleetguard.dto.DriverStatsDto;
import org.fleetguard.dto.TechnicianStatsDto;
import org.fleetguard.dto.UserStatsDto;
import org.fleetguard.entity.DefectSeverity;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.entity.WorkOrder;
import org.fleetguard.entity.WorkOrderExecutionType;
import org.fleetguard.entity.WorkOrderSourceType;
import org.fleetguard.exception.UserValidationException;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.DriverCount;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.TripRepository;
import org.fleetguard.repository.UserAmount;
import org.fleetguard.repository.UserCount;
import org.fleetguard.repository.UserRepository;
import org.fleetguard.repository.WorkOrderExpenseRepository;
import org.fleetguard.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Actividad por usuario de la sección Usuarios (CAM-91): qué hizo cada chofer y cada técnico en el período. */
class UserStatsServiceTest {

    private static final Instant FROM = Instant.parse("2026-01-01T03:00:00Z");
    private static final Instant TO = Instant.parse("2027-01-01T03:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final WorkOrderExpenseRepository expenseRepository = mock(WorkOrderExpenseRepository.class);
    private final UserStatsService service = new UserStatsService(userRepository, tripRepository, inspectionRepository,
            defectRepository, workOrderRepository, expenseRepository);

    private static void setField(Object entity, String name, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }

    private static User user(String username, Role role) throws Exception {
        User user = new User(username, "hash", role);
        setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static WorkOrder finalized(User technician, String createdAt, String finalizedAt) throws Exception {
        WorkOrder workOrder = new WorkOrder(UUID.randomUUID(), WorkOrderSourceType.MANUAL, null, null, null, "Frenos",
                null, WorkOrderExecutionType.INTERNO, null, null, technician.getId());
        workOrder.finalizeOrder("Hecho");
        setField(workOrder, "createdAt", Instant.parse(createdAt));
        setField(workOrder, "finalizedAt", Instant.parse(finalizedAt));
        return workOrder;
    }

    private UserStatsDto stats() {
        return service.stats(FROM.toString(), TO.toString());
    }

    @Test
    void cadaChoferSumaSusViajesInspeccionesYDefectos() throws Exception {
        User ana = user("ana", Role.CHOFER);
        User beto = user("beto", Role.CHOFER);
        when(userRepository.findByRoleOrderByUsernameAsc(Role.CHOFER)).thenReturn(List.of(ana, beto));
        when(tripRepository.countTripsByDriver(FROM, TO)).thenReturn(List.of(new UserCount(ana.getId(), 12)));
        when(inspectionRepository.countByDriver(FROM, TO)).thenReturn(List.of(
                new DriverCount(ana.getId().toString(), 24),
                // Inspecciones anteriores a CAM-85: el "chofer" no es un usuario, no suma a nadie.
                new DriverCount("driver-demo-1", 300)));
        when(defectRepository.countByDriver(DefectSeverity.BLOCKING, FROM, TO))
                .thenReturn(List.of(new DriverCount(ana.getId().toString(), 1)));
        when(defectRepository.countByDriver(DefectSeverity.NON_BLOCKING, FROM, TO))
                .thenReturn(List.of(new DriverCount(ana.getId().toString(), 3)));

        List<DriverStatsDto> drivers = stats().drivers();

        assertEquals(2, drivers.size());
        DriverStatsDto anaStats = drivers.get(0);
        assertEquals("ana", anaStats.username());
        assertEquals(12, anaStats.trips());
        assertEquals(24, anaStats.inspections());
        // defects es el total; los bloqueantes van aparte para poder apilarlos en el gráfico.
        assertEquals(4, anaStats.defects());
        assertEquals(1, anaStats.blockingDefects());
        DriverStatsDto betoStats = drivers.get(1);
        assertEquals(0, betoStats.trips());
        assertEquals(0, betoStats.inspections());
        assertEquals(0, betoStats.defects());
    }

    @Test
    void cadaTecnicoSumaSusOtsElTiempoPromedioYLoQueGasto() throws Exception {
        User carla = user("carla", Role.TECNICO);
        User dario = user("dario", Role.TECNICO);
        dario.setActive(false);
        when(userRepository.findByRoleOrderByUsernameAsc(Role.TECNICO)).thenReturn(List.of(carla, dario));
        when(workOrderRepository.countByTechnician(any())).thenReturn(List.of(new UserCount(carla.getId(), 2)));
        when(workOrderRepository.findFinalizedWithTechnician(FROM, TO)).thenReturn(List.of(
                finalized(carla, "2026-03-01T12:00:00Z", "2026-03-02T12:00:00Z"),
                finalized(carla, "2026-04-01T12:00:00Z", "2026-04-01T18:15:00Z")));
        when(expenseRepository.sumByTechnician(FROM, TO))
                .thenReturn(List.of(new UserAmount(carla.getId(), new BigDecimal("350000.50"))));

        List<TechnicianStatsDto> technicians = stats().technicians();

        TechnicianStatsDto carlaStats = technicians.get(0);
        assertEquals(2, carlaStats.openWorkOrders());
        assertEquals(2, carlaStats.finalizedWorkOrders());
        // 24 h y 6,25 h: promedio 15,125, con un decimal.
        assertEquals(15.1, carlaStats.avgResolutionHours());
        assertEquals(new BigDecimal("350000.50"), carlaStats.expensesTotal());
        TechnicianStatsDto darioStats = technicians.get(1);
        assertFalse(darioStats.active());
        assertEquals(0, darioStats.openWorkOrders());
        assertEquals(0, darioStats.finalizedWorkOrders());
        // Sin OTs finalizadas no hay promedio: null, no un cero que parezca "resuelve al instante".
        assertNull(darioStats.avgResolutionHours());
        assertEquals(BigDecimal.ZERO, darioStats.expensesTotal());
    }

    @Test
    void sinPeriodoSeCuentaTodoElHistorial() {
        service.stats(null, null);

        verify(tripRepository).countTripsByDriver(Instant.EPOCH, Instant.parse("9999-01-01T00:00:00Z"));
        verify(inspectionRepository).countByDriver(eq(Instant.EPOCH), any());
    }

    @Test
    void unPeriodoInvalidoDevuelve422() {
        UserValidationException ex = assertThrows(UserValidationException.class, () -> service.stats("2026-01-01", "mañana"));

        assertEquals("to", ex.getDetails().get(0).field());
    }
}
