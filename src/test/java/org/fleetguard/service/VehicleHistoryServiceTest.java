package org.fleetguard.service;

import org.fleetguard.dto.VehicleHistoryDto;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.mapper.VehicleHistoryMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    void vehiculoInexistenteOIdMalformadoLanzaNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> service.getHistory(id.toString()));
        assertThrows(VehicleNotFoundException.class, () -> service.getHistory("no-es-un-uuid"));
    }
}
