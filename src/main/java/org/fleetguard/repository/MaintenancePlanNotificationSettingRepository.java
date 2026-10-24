package org.fleetguard.repository;

import org.fleetguard.entity.MaintenancePlanNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenancePlanNotificationSettingRepository extends JpaRepository<MaintenancePlanNotificationSetting, UUID> {
}
