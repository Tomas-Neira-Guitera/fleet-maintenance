package org.example.mapper;

import org.example.dto.MaintenancePlanDto;
import org.example.entity.MaintenancePlan;
import org.springframework.stereotype.Component;

@Component
public class MaintenancePlanMapper {

    public MaintenancePlanDto toDto(MaintenancePlan plan) {
        return new MaintenancePlanDto(
                plan.getId().toString(),
                plan.getName(),
                plan.getCategory(),
                plan.getIntervalType().toJson(),
                plan.getIntervalKm(),
                plan.getIntervalDays(),
                plan.isActive()
        );
    }
}
