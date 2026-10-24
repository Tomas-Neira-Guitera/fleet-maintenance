package org.fleetguard.service;

import org.fleetguard.dto.NotificationSettingDto;
import org.fleetguard.dto.UpdateNotificationSettingRequest;
import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationSetting;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.exception.NotificationNotFoundException;
import org.fleetguard.exception.NotificationValidationException;
import org.fleetguard.repository.NotificationSettingRepository;
import org.fleetguard.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-117: configuración por tipo de aviso. */
class NotificationSettingsServiceTest {

    private final NotificationSettingRepository repository = mock(NotificationSettingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationSettingsService service = new NotificationSettingsService(repository, userRepository,
            Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC));

    private static User user() throws Exception {
        User user = new User("juan", "hash", Role.TECNICO);
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, UUID.randomUUID());
        return user;
    }

    private static UpdateNotificationSettingRequest request(Boolean enabled, List<String> channels, List<String> roles,
                                                            List<String> userIds, Integer leadDays, Integer leadKm) {
        return new UpdateNotificationSettingRequest(enabled, channels, roles, userIds, leadDays, leadKm);
    }

    @Test
    void sinNadaGuardadoListaElCatalogoConSusDefaults() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        List<NotificationSettingDto> list = service.list();

        assertEquals(NotificationType.values().length, list.size());
        NotificationSettingDto first = list.get(0);
        assertEquals("blocking_defect_reported", first.type());
        assertEquals("configurable", first.audience());
        assertEquals(List.of("in_app", "push"), first.channels());
        assertEquals(List.of("ADMIN"), first.recipientRoles());
        assertFalse(first.customized());
        NotificationSettingDto dueSoon = list.stream().filter(d -> d.type().equals("maintenance_due_soon")).findFirst().orElseThrow();
        assertTrue(dueSoon.supportsLead());
        assertEquals(7, dueSoon.leadDays());
        assertEquals(1000, dueSoon.leadKm());
        NotificationSettingDto assigned = list.stream().filter(d -> d.type().equals("work_order_assigned")).findFirst().orElseThrow();
        assertEquals("owner", assigned.audience());
        assertEquals(List.of(), assigned.recipientRoles());
    }

    @Test
    void guardaCanalesYDestinatarios() throws Exception {
        User technician = user();
        when(repository.findById(NotificationType.WORK_ORDER_FINALIZED)).thenReturn(Optional.empty());
        when(userRepository.findAllById(any())).thenReturn(List.of(technician));

        NotificationSettingDto dto = service.update("work_order_finalized", request(true, List.of("email", "in_app"),
                List.of("ADMIN"), List.of(technician.getId().toString()), null, null));

        ArgumentCaptor<NotificationSetting> saved = ArgumentCaptor.forClass(NotificationSetting.class);
        verify(repository).save(saved.capture());
        assertEquals(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL), saved.getValue().getChannels());
        assertEquals(Set.of(technician.getId()), saved.getValue().getRecipientUserIds());
        assertEquals(List.of("in_app", "email"), dto.channels());
        assertTrue(dto.customized());
    }

    @Test
    void validaCanalesRolesUsuariosYAnticipacion() {
        when(userRepository.findAllById(any())).thenReturn(List.of());

        NotificationValidationException ex = assertThrows(NotificationValidationException.class, () ->
                service.update("maintenance_due_soon", request(true, List.of("sms"), List.of("JEFE"),
                        List.of("x", UUID.randomUUID().toString()), 0, null)));

        List<String> fields = ex.getDetails().stream().map(d -> d.field()).toList();
        assertTrue(fields.containsAll(List.of("channels", "recipientRoles", "recipientUserIds", "leadDays", "leadKm")));
        verify(repository, never()).save(any());
    }

    @Test
    void activoSinCanalesNiDestinatariosNoSeAcepta() {
        NotificationValidationException ex = assertThrows(NotificationValidationException.class, () ->
                service.update("blocking_defect_reported", request(true, List.of(), List.of(), List.of(), null, null)));
        assertEquals(2, ex.getDetails().size());
    }

    @Test
    void apagadoNoPideCanalesNiDestinatarios() {
        when(repository.findById(NotificationType.BLOCKING_DEFECT_REPORTED)).thenReturn(Optional.empty());

        NotificationSettingDto dto = service.update("blocking_defect_reported", request(false, List.of(), List.of(), List.of(), null, null));

        assertFalse(dto.enabled());
    }

    @Test
    void unAvisoDelInvolucradoNoAceptaDestinatariosNiAnticipacion() {
        NotificationValidationException ex = assertThrows(NotificationValidationException.class, () ->
                service.update("work_order_assigned", request(true, List.of("in_app"), List.of("ADMIN"), null, 3, null)));
        List<String> fields = ex.getDetails().stream().map(d -> d.field()).toList();
        assertEquals(List.of("recipientRoles", "leadDays"), fields);
    }

    @Test
    void enabledEsObligatorio() {
        NotificationValidationException ex = assertThrows(NotificationValidationException.class, () ->
                service.update("work_order_assigned", request(null, List.of("in_app"), null, null, null, null)));
        assertEquals("enabled", ex.getDetails().get(0).field());
    }

    @Test
    void unTipoQueNoExisteEs404() {
        NotificationNotFoundException ex = assertThrows(NotificationNotFoundException.class,
                () -> service.update("no_existe", request(true, null, null, null, null, null)));
        assertEquals("NOTIFICATION_TYPE_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void restablecerBorraLaFilaYDevuelveElDefault() {
        NotificationSettingDto dto = service.reset("trip_open_reminder");

        verify(repository).deleteById(NotificationType.TRIP_OPEN_REMINDER);
        assertFalse(dto.customized());
        assertTrue(dto.enabled());
    }

    @Test
    void laConfiguracionGuardadaPisaAlDefault() {
        NotificationSetting row = new NotificationSetting(NotificationType.TRIP_OPEN_REMINDER);
        row.apply(false, Set.of(NotificationChannel.EMAIL), Set.of(), Set.of(), null, null, Instant.now());
        when(repository.findById(NotificationType.TRIP_OPEN_REMINDER)).thenReturn(Optional.of(row));

        EffectiveNotificationSetting effective = service.effective(NotificationType.TRIP_OPEN_REMINDER);

        assertFalse(effective.enabled());
        assertEquals(Set.of(NotificationChannel.EMAIL), effective.channels());
        assertTrue(effective.customized());
    }
}
