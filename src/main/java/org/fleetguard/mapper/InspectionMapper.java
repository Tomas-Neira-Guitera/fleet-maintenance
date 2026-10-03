package org.fleetguard.mapper;

import org.fleetguard.entity.Inspection;
import org.fleetguard.entity.InspectionAnswer;
import org.fleetguard.entity.Defect;
import org.fleetguard.dto.ChecklistAnswerDto;
import org.fleetguard.dto.DefectDetailDto;
import org.fleetguard.dto.InspectionDto;
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
            defectDto = new DefectDetailDto(defect.getSeverity().toJson(), defect.getDescription(), defect.getPhotoUrl(),
                    defect.getDetails());
        }
        return new ChecklistAnswerDto(
                answer.getItemId(),
                answer.getOutcome() == null ? null : answer.getOutcome().toJson(),
                answer.getNumberValue(),
                defectDto
        );
    }
}
