package org.fleetguard.mapper;

import org.fleetguard.dto.DefectDto;
import org.fleetguard.entity.Defect;
import org.springframework.stereotype.Component;

@Component
public class DefectMapper {

    public DefectDto toDto(Defect defect, String vehicleId, String vehiclePlate) {
        String reportedBy = defect.getInspectionAnswer().getInspection().getDriverName();
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
