package org.example.mapper;

import org.example.entity.Inspection;
import org.example.entity.InspectionAnswer;
import org.example.entity.Defect;
import org.example.dto.ChecklistAnswerDto;
import org.example.dto.DefectDetailDto;
import org.example.dto.InspectionDto;
import org.springframework.stereotype.Component;

@Component
public class InspectionMapper {

    public InspectionDto toDto(Inspection inspection) {
        return new InspectionDto(
                inspection.getId().toString(),
                inspection.getTrip().getId().toString(),
                inspection.getVehicleId().toString(),
                inspection.getDriverId(),
                inspection.getType().toJson(),
                inspection.getTimestamp().toString(),
                inspection.getOdometerKm(),
                inspection.getAnswers().stream().map(this::toAnswerDto).toList(),
                inspection.getNotes(),
                inspection.isHasBlockingDefect()
        );
    }

    private ChecklistAnswerDto toAnswerDto(InspectionAnswer answer) {
        DefectDetailDto defectDto = null;
        Defect defect = answer.getDefect();
        if (defect != null) {
            defectDto = new DefectDetailDto(defect.getSeverity().toJson(), defect.getDescription(), defect.getPhotoUrl());
        }
        return new ChecklistAnswerDto(
                answer.getItemId(),
                answer.getOutcome() == null ? null : answer.getOutcome().toJson(),
                answer.getNumberValue(),
                defectDto
        );
    }
}
