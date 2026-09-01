package org.example.mapper;

import org.example.dto.DefectDto;
import org.example.entity.Defect;
import org.springframework.stereotype.Component;

@Component
public class DefectMapper {

    public DefectDto toDto(Defect defect, String vehiclePlate) {
        return new DefectDto(
                defect.getId().toString(),
                defect.getSeverity().toJson(),
                defect.getDescription(),
                defect.getPhotoUrl(),
                defect.getCreatedAt().toString(),
                vehiclePlate,
                defect.getStatus()
        );
    }
}
