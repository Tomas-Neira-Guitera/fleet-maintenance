package org.fleetguard.mapper;

import org.fleetguard.dto.CompletionDto;
import org.fleetguard.entity.MaintenanceCompletion;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceCompletionMapper {

    public CompletionDto toDto(MaintenanceCompletion completion) {
        return new CompletionDto(
                completion.getId().toString(),
                completion.getCompletedAt().toString(),
                completion.getCompletedKm(),
                completion.getWorkOrderId(),
                completion.getNotes()
        );
    }
}
