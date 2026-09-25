package org.example.service;

import org.example.dto.DefectDto;
import org.example.dto.VehicleHistoryDto;
import org.example.entity.Vehicle;
import org.example.exception.VehicleNotFoundException;
import org.example.mapper.DefectMapper;
import org.example.mapper.VehicleHistoryMapper;
import org.example.repository.DefectRepository;
import org.example.repository.InspectionRepository;
import org.example.repository.MaintenanceCompletionRepository;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Lógica de GET /api/vehicles/{id}/history -- ver openapi.yaml y CAM-22-vehicle-history-contract.md. */
@Service
public class VehicleHistoryService {

    private final VehicleRepository vehicleRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectRepository defectRepository;
    private final MaintenanceCompletionRepository completionRepository;
    private final VehicleHistoryMapper historyMapper;
    private final DefectMapper defectMapper;

    public VehicleHistoryService(VehicleRepository vehicleRepository, InspectionRepository inspectionRepository,
                                  DefectRepository defectRepository, MaintenanceCompletionRepository completionRepository,
                                  VehicleHistoryMapper historyMapper, DefectMapper defectMapper) {
        this.vehicleRepository = vehicleRepository;
        this.inspectionRepository = inspectionRepository;
        this.defectRepository = defectRepository;
        this.completionRepository = completionRepository;
        this.historyMapper = historyMapper;
        this.defectMapper = defectMapper;
    }

    @Transactional(readOnly = true)
    public VehicleHistoryDto getHistory(String id) {
        UUID vehicleId = parseId(id);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(id));

        List<DefectDto> defects = defectRepository.findByVehicleIdWithInspection(vehicleId).stream()
                .map(defect -> defectMapper.toDto(defect, vehicleId.toString(), vehicle.getPlate()))
                .toList();

        return new VehicleHistoryDto(
                inspectionRepository.findByVehicleIdOrderByTimestampDesc(vehicleId).stream()
                        .map(historyMapper::toInspectionItem)
                        .toList(),
                defects,
                completionRepository.findByVehicleIdWithPlan(vehicleId).stream()
                        .map(historyMapper::toMaintenanceItem)
                        .toList()
        );
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new VehicleNotFoundException(id);
        }
    }
}
