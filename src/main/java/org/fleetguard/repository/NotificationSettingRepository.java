package org.fleetguard.repository;

import org.fleetguard.entity.NotificationSetting;
import org.fleetguard.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, NotificationType> {
}
