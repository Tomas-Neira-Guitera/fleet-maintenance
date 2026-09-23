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
        return listDefects(null, null);
    }

    /**
     * vehicleId filtra por vehículo (historial de CAM-15, selector de CAM-14); status filtra
     * por estado ("open"/"resuelto"). Ambos opcionales, sin filtro por default.
     */
    public List<DefectDto> listDefects(String vehicleIdParam, String statusParam) {
        List<Defect> defects = vehicleIdParam != null && !vehicleIdParam.isBlank()
                ? defectRepository.findByVehicleIdWithInspection(UUID.fromString(vehicleIdParam))
                : defectRepository.findAllWithInspection();

        if (statusParam != null) {
            defects = defects.stream().filter(d -> statusParam.equals(d.getStatus())).toList();
        }

        Map<UUID, String> plateByVehicleId = vehicleRepository
                .findAllById(defects.stream().map(this::vehicleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Vehicle::getPlate));

        return defects.stream()
                .sorted(Comparator.comparing(Defect::getSeverity).reversed()
                        .thenComparing(Defect::getCreatedAt, Comparator.reverseOrder()))
                .map(defect -> defectMapper.toDto(defect, vehicleId(defect).toString(), plateByVehicleId.get(vehicleId(defect))))
                .toList();
    }

    private UUID vehicleId(Defect defect) {
        return defect.getInspectionAnswer().getInspection().getVehicleId();
    }
}
