package org.fleetguard.service;

import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.VehicleHistoryDto;
import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.Vehicle;
import org.fleetguard.exception.VehicleNotFoundException;
import org.fleetguard.mapper.DefectMapper;
import org.fleetguard.mapper.VehicleHistoryMapper;
import org.fleetguard.repository.DefectRepository;
import org.fleetguard.repository.InspectionRepository;
import org.fleetguard.repository.MaintenanceCompletionRepository;
import org.fleetguard.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lógica de GET /api/vehicles/{id}/history -- ver openapi.yaml y CAM-22-vehicle-history-contract.md. */
@Service
public class VehicleHistoryService {

    private final VehicleRepository vehicleRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectRepository defectRepository;
    private final MaintenanceCompletionRepository completionRepository;
    private final UserNameService userNameService;
    private final VehicleHistoryMapper historyMapper;
    private final DefectMapper defectMapper;

    public VehicleHistoryService(VehicleRepository vehicleRepository, InspectionRepository inspectionRepository,
                                  DefectRepository defectRepository, MaintenanceCompletionRepository completionRepository,
                                  UserNameService userNameService, VehicleHistoryMapper historyMapper,
                                  DefectMapper defectMapper) {
        this.vehicleRepository = vehicleRepository;
        this.inspectionRepository = inspectionRepository;
        this.defectRepository = defectRepository;
        this.completionRepository = completionRepository;
        this.userNameService = userNameService;
        this.historyMapper = historyMapper;
        this.defectMapper = defectMapper;
    }

    @Transactional(readOnly = true)
    public VehicleHistoryDto getHistory(String id) {
        UUID vehicleId = parseId(id);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(id));

        // Todo defecto sale de una inspección del mismo vehículo: con los choferes de las
        // inspecciones alcanza para resolver también quién reportó cada defecto.
        List<Inspection> inspections = inspectionRepository.findByVehicleIdOrderByTimestampDesc(vehicleId);
        Map<String, String> driverNames = userNameService.currentNames(
                inspections.stream().map(Inspection::getDriverId).toList());

        List<DefectDto> defects = defectRepository.findByVehicleIdWithInspection(vehicleId).stream()
                .map(defect -> defectMapper.toDto(defect, vehicleId.toString(), vehicle.getPlate(), driverNames))
                .toList();

        return new VehicleHistoryDto(
                inspections.stream()
                        .map(inspection -> historyMapper.toInspectionItem(inspection, driverNames))
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
