package org.fleetguard.mapper;

import org.fleetguard.dto.DefectDto;
import org.fleetguard.entity.Defect;
import org.fleetguard.entity.Inspection;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefectMapper {

    /** currentNames: nombre actual por id de usuario (UserNameService); sin dato, el guardado en la inspección. */
    public DefectDto toDto(Defect defect, String vehicleId, String vehiclePlate, Map<String, String> currentNames) {
        Inspection inspection = defect.getInspectionAnswer().getInspection();
        String reportedBy = currentNames.getOrDefault(inspection.getDriverId(), inspection.getDriverName());
        return new DefectDto(
                defect.getId().toString(),
                defect.getSeverity().toJson(),
                defect.getDescription(),
                defect.getPhotoUrl(),
                defect.getCreatedAt().toString(),
                vehicleId,
                vehiclePlate,
                defect.getStatus(),
                reportedBy,
                defect.getDetails()
        );
    }
}
