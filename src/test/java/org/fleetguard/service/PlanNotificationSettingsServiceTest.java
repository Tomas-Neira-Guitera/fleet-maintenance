package org.fleetguard.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-118: avisos de vencimiento propios de un plan. */
class PlanNotificationSettingsServiceTest {

    private final MaintenancePlanRepository planRepository = mock(MaintenancePlanRepository.class);
    private final MaintenancePlanNotificationSettingRepository repository = mock(MaintenancePlanNotificationSettingRepository.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final PlanNotificationSettingsService service = new PlanNotificationSettingsService(planRepository, repository,
            settings, Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC));

    private MaintenancePlan plan(IntervalType type) throws Exception {
        MaintenancePlan plan = new MaintenancePlan("Aceite", null, type, type == IntervalType.TIME ? null : 10_000,
                type == IntervalType.KM ? null : 180);
        Field id = MaintenancePlan.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(plan, UUID.randomUUID());
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        return plan;
    }

    @BeforeEach
    void setUp() {
        when(settings.effective(NotificationType.MAINTENANCE_DUE_SOON))
                .thenReturn(NotificationCatalog.of(NotificationType.MAINTENANCE_DUE_SOON).defaults());
        when(settings.effective(NotificationType.MAINTENANCE_OVERDUE))
                .thenReturn(NotificationCatalog.of(NotificationType.MAINTENANCE_OVERDUE).defaults());
    }

    @Test
    void sinConfiguracionPropiaMuestraLaGeneral() throws Exception {
        MaintenancePlan plan = plan(IntervalType.BOTH);
        when(repository.findById(plan.getId())).thenReturn(Optional.empty());

        PlanNotificationSettingsDto dto = service.get(plan.getId().toString());

        assertFalse(dto.custom());
        assertNull(dto.enabled());
        assertEquals("both", dto.intervalType());
        assertEquals(7, dto.general().leadDays());
        assertEquals(1000, dto.general().leadKm());
        assertEquals(List.of("in_app", "push"), dto.general().channels());
    }

    @Test
    void guardaLaConfiguracionPropia() throws Exception {
        MaintenancePlan plan = plan(IntervalType.BOTH);
        when(repository.findById(plan.getId())).thenReturn(Optional.empty());

        PlanNotificationSettingsDto dto = service.update(plan.getId().toString(),
                new UpdatePlanNotificationSettingsRequest(true, List.of("email"), 15, 2000));

        assertTrue(dto.custom());
        assertEquals(15, dto.leadDays());
        assertEquals(List.of("email"), dto.channels());
        verify(repository).save(any(MaintenancePlanNotificationSetting.class));
    }

    @Test
    void laAnticipacionTieneQueCorresponderAlTipoDeIntervalo() throws Exception {
        MaintenancePlan kmPlan = plan(IntervalType.KM);

        NotificationValidationException ex = assertThrows(NotificationValidationException.class, () ->
                service.update(kmPlan.getId().toString(), new UpdatePlanNotificationSettingsRequest(true, List.of("in_app"), 10, null)));

        assertEquals(List.of("leadDays", "leadKm"), ex.getDetails().stream().map(d -> d.field()).toList());
        verify(repository, never()).save(any());
    }

    @Test
    void activoSinCanalesOSinEnabledNoSeAcepta() throws Exception {
        MaintenancePlan timePlan = plan(IntervalType.TIME);

        NotificationValidationException ex = assertThrows(NotificationValidationException.class, () ->
                service.update(timePlan.getId().toString(), new UpdatePlanNotificationSettingsRequest(true, List.of(), 10, null)));
        assertEquals("channels", ex.getDetails().get(0).field());

        ex = assertThrows(NotificationValidationException.class, () ->
                service.update(timePlan.getId().toString(), new UpdatePlanNotificationSettingsRequest(null, List.of(), 10, null)));
        assertEquals("enabled", ex.getDetails().get(0).field());
    }

    @Test
    void restablecerVuelveALaGeneral() throws Exception {
        MaintenancePlan plan = plan(IntervalType.TIME);

        PlanNotificationSettingsDto dto = service.reset(plan.getId().toString());

        verify(repository).deleteById(plan.getId());
        assertFalse(dto.custom());
    }

    @Test
    void unPlanQueNoExisteEs404() {
        when(planRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(MaintenancePlanNotFoundException.class, () -> service.get(UUID.randomUUID().toString()));
        assertThrows(MaintenancePlanNotFoundException.class, () -> service.get("no-es-uuid"));
    }

    @Test
    void alertConfigDiceSiElPlanTieneConfiguracionPropia() {
        UUID planId = UUID.randomUUID();
        when(repository.findById(planId)).thenReturn(Optional.empty());
        assertFalse(service.alertConfig(planId).custom());

        MaintenancePlanNotificationSetting setting = new MaintenancePlanNotificationSetting(planId);
        setting.apply(true, 20, null, Set.of(NotificationChannel.PUSH), Instant.now());
        when(repository.findById(planId)).thenReturn(Optional.of(setting));
        PlanNotificationSettingsService.AlertConfig config = service.alertConfig(planId);
        assertTrue(config.custom());
        assertEquals(20, config.leadDays());
        assertEquals(Set.of(NotificationChannel.PUSH), config.channels());
    }
}
