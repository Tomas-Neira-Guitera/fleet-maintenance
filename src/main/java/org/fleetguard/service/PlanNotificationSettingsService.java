package org.fleetguard.service;

import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.PlanNotificationSettingsDto;
import org.fleetguard.dto.UpdatePlanNotificationSettingsRequest;
import org.fleetguard.entity.IntervalType;
import org.fleetguard.entity.MaintenancePlan;
import org.fleetguard.entity.MaintenancePlanNotificationSetting;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.exception.MaintenancePlanNotFoundException;
import org.fleetguard.exception.NotificationValidationException;
import org.fleetguard.repository.MaintenancePlanNotificationSettingRepository;
import org.fleetguard.repository.MaintenancePlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Avisos de vencimiento propios de un plan (CAM-118): anticipación y canales distintos de los generales.
 * La anticipación por días solo aplica a planes por tiempo, la de km a planes por km.
 */
@Service
public class PlanNotificationSettingsService {

    /** Cómo avisa un plan: la suya (custom) o la general. */
    public record AlertConfig(boolean custom, boolean enabled, Set<NotificationChannel> channels,
                              Integer leadDays, Integer leadKm) {
    }

    private final MaintenancePlanRepository planRepository;
    private final MaintenancePlanNotificationSettingRepository repository;
    private final NotificationSettingsService settings;
    private final Clock clock;

    public PlanNotificationSettingsService(MaintenancePlanRepository planRepository,
                                           MaintenancePlanNotificationSettingRepository repository,
                                           NotificationSettingsService settings, Clock clock) {
        this.planRepository = planRepository;
        this.repository = repository;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlanNotificationSettingsDto get(String planId) {
        MaintenancePlan plan = findPlan(planId);
        return toDto(plan, repository.findById(plan.getId()).orElse(null));
    }

    @Transactional
    public PlanNotificationSettingsDto update(String planId, UpdatePlanNotificationSettingsRequest request) {
        MaintenancePlan plan = findPlan(planId);
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        if (request.enabled() == null) {
            details.add(new FieldValidationErrorDetail("enabled", "Obligatorio"));
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        Set<NotificationChannel> channels = NotificationSettingsService.parseChannels(request.channels(), details);
        if (enabled && channels.isEmpty()) {
            details.add(new FieldValidationErrorDetail("channels", "Elegí al menos un canal"));
        }
        IntervalType interval = plan.getIntervalType();
        validateLead("leadDays", request.leadDays(), interval != IntervalType.KM, NotificationSettingsService.MAX_LEAD_DAYS,
                "Este plan no vence por tiempo", details);
        validateLead("leadKm", request.leadKm(), interval != IntervalType.TIME, NotificationSettingsService.MAX_LEAD_KM,
                "Este plan no vence por kilometraje", details);
        if (!details.isEmpty()) {
            throw new NotificationValidationException("Configuración de avisos del plan inválida", details);
        }

        MaintenancePlanNotificationSetting setting = repository.findById(plan.getId())
                .orElseGet(() -> new MaintenancePlanNotificationSetting(plan.getId()));
        setting.apply(enabled, request.leadDays(), request.leadKm(), channels, clock.instant());
        repository.save(setting);
        return toDto(plan, setting);
    }

    /** El plan vuelve a usar la configuración general. */
    @Transactional
    public PlanNotificationSettingsDto reset(String planId) {
        MaintenancePlan plan = findPlan(planId);
        repository.deleteById(plan.getId());
        return toDto(plan, null);
    }

    @Transactional(readOnly = true)
    public AlertConfig alertConfig(UUID planId) {
        return repository.findById(planId)
                .map(s -> new AlertConfig(true, s.isEnabled(), s.getChannels(), s.getLeadDays(), s.getLeadKm()))
                .orElseGet(() -> new AlertConfig(false, false, null, null, null));
    }

    private static void validateLead(String field, Integer value, boolean applies, int max, String notApplicable,
                                     List<FieldValidationErrorDetail> details) {
        if (applies) {
            NotificationSettingsService.validateLead(field, value, max, details);
        } else if (value != null) {
            details.add(new FieldValidationErrorDetail(field, notApplicable));
        }
    }

    private MaintenancePlan findPlan(String planId) {
        UUID id;
        try {
            id = UUID.fromString(planId);
        } catch (IllegalArgumentException e) {
            throw new MaintenancePlanNotFoundException(planId);
        }
        return planRepository.findById(id).orElseThrow(() -> new MaintenancePlanNotFoundException(planId));
    }

    private PlanNotificationSettingsDto toDto(MaintenancePlan plan, MaintenancePlanNotificationSetting setting) {
        EffectiveNotificationSetting dueSoon = settings.effective(NotificationType.MAINTENANCE_DUE_SOON);
        EffectiveNotificationSetting overdue = settings.effective(NotificationType.MAINTENANCE_OVERDUE);
        PlanNotificationSettingsDto.General general = new PlanNotificationSettingsDto.General(dueSoon.enabled(),
                overdue.enabled(), NotificationSettingsService.channelsToJson(dueSoon.channels()),
                dueSoon.leadDays(), dueSoon.leadKm());
        if (setting == null) {
            return new PlanNotificationSettingsDto(plan.getId().toString(), plan.getIntervalType().toJson(), false,
                    null, null, null, null, general);
        }
        return new PlanNotificationSettingsDto(plan.getId().toString(), plan.getIntervalType().toJson(), true,
                setting.isEnabled(), NotificationSettingsService.channelsToJson(setting.getChannels()),
                setting.getLeadDays(), setting.getLeadKm(), general);
    }
}
