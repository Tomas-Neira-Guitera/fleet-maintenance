package org.fleetguard.service;

import org.fleetguard.dto.InspectionHistoryItemDto;
import org.fleetguard.dto.MaintenanceHistoryItemDto;
import org.fleetguard.dto.VehicleHistoryDto;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionType;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenanceCompletion;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.Trip;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.entity.VehicleMaintenanceAssignment;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.mapper.VehicleHistoryMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleHistoryServiceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final InspectionRepository inspectionRepository = mock(InspectionRepository.class);
    private final DefectRepository defectRepository = mock(DefectRepository.class);
    private final MaintenanceCompletionRepository completionRepository = mock(MaintenanceCompletionRepository.class);
    private final VehicleHistoryService service = new VehicleHistoryService(vehicleRepository, inspectionRepository,
            defectRepository, completionRepository, new VehicleHistoryMapper(), new DefectMapper());

    @Test
    void vehiculoSinHistorialDevuelveTresListasVacias() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(new Vehicle("AB123CD", "Ford", "Cargo")));
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(id)).thenReturn(List.of());
        when(defectRepository.findByVehicleIdWithInspection(id)).thenReturn(List.of());
        when(completionRepository.findByVehicleIdWithPlan(id)).thenReturn(List.of());

        VehicleHistoryDto result = service.getHistory(id.toString());

        assertTrue(result.inspections().isEmpty());
        assertTrue(result.defects().isEmpty());
        assertTrue(result.maintenance().isEmpty());
    }

    @Test
    void elHistorialMuestraElChoferDeCadaInspeccionYElPlanDeCadaMantenimiento() throws Exception {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("AB123CD", "Ford", "Cargo");
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));

        Inspection inspection = new Inspection(new Trip(vehicle, Instant.now()), id, "driver-1", "Carlos Gómez",
                InspectionType.POST_TRIP, Instant.parse("2026-10-03T18:00:00Z"), 50500.0, "Ruido en frenos", true);
        setId(inspection, UUID.randomUUID());
        when(inspectionRepository.findByVehicleIdOrderByTimestampDesc(id)).thenReturn(List.of(inspection));

        MaintenancePlan plan = new MaintenancePlan("Cambio de aceite", "motor", IntervalType.KM, 10000, null);
        VehicleMaintenanceAssignment assignment = new VehicleMaintenanceAssignment(id, plan, 40000L, null);
        MaintenanceCompletion completion = new MaintenanceCompletion(assignment, LocalDate.of(2026, 9, 1), 49000L,
                "wo-1", "Registrado automáticamente al finalizar la orden de trabajo");
        setId(completion, UUID.randomUUID());
        when(completionRepository.findByVehicleIdWithPlan(id)).thenReturn(List.of(completion));
        when(defectRepository.findByVehicleIdWithInspection(id)).thenReturn(List.of());

        VehicleHistoryDto result = service.getHistory(id.toString());

        InspectionHistoryItemDto item = result.inspections().get(0);
        assertEquals("post-trip", item.type());
        assertEquals("Carlos Gómez", item.driverName());
        assertEquals(50500.0, item.odometerKm());
        assertTrue(item.hasBlockingDefect());
        MaintenanceHistoryItemDto done = result.maintenance().get(0);
        assertEquals("Cambio de aceite", done.planName());
        assertEquals("2026-09-01", done.completedAt());
        // CAM-60: el mantenimiento que registró una OT queda vinculado a ella.
        assertEquals("wo-1", done.workOrderId());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void vehiculoInexistenteOIdMalformadoLanzaNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> service.getHistory(id.toString()));
        assertThrows(VehicleNotFoundException.class, () -> service.getHistory("no-es-un-uuid"));
    }
}
