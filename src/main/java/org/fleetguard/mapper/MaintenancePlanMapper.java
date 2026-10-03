package org.fleetguard.mapper;

import org.fleetguard.dto.MaintenancePlanDto;
import org.fleetguard.entity.MaintenancePlan;
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
