package org.example.service;

import org.example.dto.DefectDto;
import org.example.entity.Defect;
import org.example.entity.Vehicle;
import org.example.mapper.DefectMapper;
import org.example.repository.DefectRepository;
import org.example.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lógica de negocio de GET /api/defects -- ver openapi.yaml. */
@Service
public class DefectService {

    private final DefectRepository defectRepository;
    private final VehicleRepository vehicleRepository;
    private final DefectMapper defectMapper;

    public DefectService(DefectRepository defectRepository, VehicleRepository vehicleRepository, DefectMapper defectMapper) {
        this.defectRepository = defectRepository;
        this.vehicleRepository = vehicleRepository;
        this.defectMapper = defectMapper;
    }

    /** Ordena por severidad (blocking primero) y luego por fecha, más reciente primero. */
    public List<DefectDto> listDefects() {
        List<Defect> defects = defectRepository.findAllWithInspection();

        Map<UUID, String> plateByVehicleId = vehicleRepository
                .findAllById(defects.stream().map(this::vehicleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Vehicle::getPlate));

        return defects.stream()
                .sorted(Comparator.comparing(Defect::getSeverity).reversed()
                        .thenComparing(Defect::getCreatedAt, Comparator.reverseOrder()))
                .map(defect -> defectMapper.toDto(defect, plateByVehicleId.get(vehicleId(defect))))
                .toList();
    }

    private UUID vehicleId(Defect defect) {
        return defect.getInspectionAnswer().getInspection().getVehicleId();
    }
}
