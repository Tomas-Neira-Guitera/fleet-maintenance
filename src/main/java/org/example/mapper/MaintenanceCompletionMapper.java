package org.example.mapper;

import org.example.dto.CompletionDto;
import org.example.entity.MaintenanceCompletion;
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
