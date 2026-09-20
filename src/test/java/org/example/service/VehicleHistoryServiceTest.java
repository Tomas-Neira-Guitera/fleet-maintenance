package org.example.service;

import org.example.dto.VehicleHistoryDto;
import org.example.entity.Vehicle;
import org.example.exception.VehicleNotFoundException;
import org.example.mapper.DefectMapper;
import org.example.mapper.VehicleHistoryMapper;
import org.example.repository.DefectRepository;
import org.example.repository.InspectionRepository;
import org.example.repository.MaintenanceCompletionRepository;
import org.example.repository.VehicleRepository;
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
