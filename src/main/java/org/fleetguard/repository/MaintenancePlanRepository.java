package org.fleetguard.repository;

import org.fleetguard.entity.MaintenancePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, UUID> {
    List<MaintenancePlan> findByActive(boolean active);
    List<MaintenancePlan> findByActiveAndCategory(boolean active, String category);
}
