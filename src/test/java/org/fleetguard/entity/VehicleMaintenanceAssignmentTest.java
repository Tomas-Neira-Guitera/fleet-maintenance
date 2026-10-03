package org.fleetguard.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Cálculo del próximo vencimiento de un plan asignado a un vehículo (CAM-40), que vive en la
 * entidad. De este cálculo dependen el estado de la flota, el dashboard y el calendario: los
 * services lo usan, pero las reglas del cálculo en sí se prueban acá.
 */
class VehicleMaintenanceAssignmentTest {

    private static VehicleMaintenanceAssignment assignment(IntervalType type, Integer km, Integer days,
                                                           Long lastKm, LocalDate lastDate) {
        MaintenancePlan plan = new MaintenancePlan("Plan", null, type, km, days);
        VehicleMaintenanceAssignment assignment = new VehicleMaintenanceAssignment(UUID.randomUUID(), plan, lastKm, lastDate);
        assignment.recalculateNextDue();
        return assignment;
    }

    @Test
    void elProximoVencimientoSumaElIntervaloALaUltimaVez() {
        VehicleMaintenanceAssignment both = assignment(IntervalType.BOTH, 10000, 180, 40000L, LocalDate.of(2026, 1, 1));

        assertEquals(50000L, both.getNextDueKm());
        assertEquals(LocalDate.of(2026, 6, 30), both.getNextDueDate());
    }

    @Test
    void sinUltimaVezOSinIntervaloNoHayVencimientoPorEseCriterio() {
        // Plan por km: no hay vencimiento por fecha aunque se conozca la fecha.
        VehicleMaintenanceAssignment byKm = assignment(IntervalType.KM, 10000, null, 40000L, LocalDate.of(2026, 1, 1));
        assertEquals(50000L, byKm.getNextDueKm());
        assertNull(byKm.getNextDueDate());

        // Sin kilometraje de la última vez no se puede calcular el próximo por km.
        VehicleMaintenanceAssignment unknownKm = assignment(IntervalType.BOTH, 10000, 30, null, LocalDate.of(2026, 1, 1));
        assertNull(unknownKm.getNextDueKm());
        assertEquals(LocalDate.of(2026, 1, 31), unknownKm.getNextDueDate());
    }

    @Test
    void recalcularDespuesDeCambiarElPlanUsaElIntervaloNuevo() {
        VehicleMaintenanceAssignment assignment = assignment(IntervalType.KM, 10000, null, 40000L, null);

        assignment.getMaintenancePlan().setIntervalKm(15000);
        assignment.recalculateNextDue();

        assertEquals(55000L, assignment.getNextDueKm());
    }
}
