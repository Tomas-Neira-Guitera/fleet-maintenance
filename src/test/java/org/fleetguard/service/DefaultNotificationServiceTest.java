package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-117: a quién y por qué canales sale cada aviso según la configuración. */
class DefaultNotificationServiceTest {

    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationSender inbox = sender(NotificationChannel.IN_APP);
    private final NotificationSender push = sender(NotificationChannel.PUSH);
    private final NotificationSender email = sender(NotificationChannel.EMAIL);
    private final DefaultNotificationService service = new DefaultNotificationService(settings, userRepository,
            List.of(inbox, push, email));

    private User admin;
    private User inactiveAdmin;
    private User technician;
    private User chofer;

    private static NotificationSender sender(NotificationChannel channel) {
        NotificationSender sender = mock(NotificationSender.class);
        when(sender.channel()).thenReturn(channel);
        return sender;
    }

    private static User user(String name, Role role, boolean active) throws Exception {
        User user = new User(name, "hash", role);
        user.setActive(active);
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, UUID.randomUUID());
        return user;
    }

    private void configure(NotificationType type, boolean enabled, Set<NotificationChannel> channels, Set<Role> roles,
                           Set<UUID> userIds) {
        when(settings.effective(type)).thenReturn(new EffectiveNotificationSetting(type, enabled, channels, roles, userIds,
                null, null, true));
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = user("admin", Role.ADMIN, true);
        inactiveAdmin = user("viejo", Role.ADMIN, false);
        technician = user("tecnico", Role.TECNICO, true);
        chofer = user("chofer", Role.CHOFER, true);
        when(userRepository.findAll()).thenReturn(List.of(admin, inactiveAdmin, technician, chofer));
    }

    @Test
    void porDefectoLlegaALosAdminsActivosPorCampanitaYPush() {
        when(settings.effective(NotificationType.BLOCKING_DEFECT_REPORTED))
                .thenReturn(NotificationCatalog.of(NotificationType.BLOCKING_DEFECT_REPORTED).defaults());

        service.notifyConfiguredRecipients(NotificationType.BLOCKING_DEFECT_REPORTED, "T", "M", "/vehiculos/1");

        NotificationContent content = new NotificationContent(NotificationType.BLOCKING_DEFECT_REPORTED, "T", "M", "/vehiculos/1");
        verify(inbox).send(List.of(admin), content);
        verify(push).send(List.of(admin), content);
        verify(email, never()).send(any(), any());
    }

    @Test
    void losDestinatariosSonLosRolesMasLosUsuariosElegidos() {
        configure(NotificationType.WORK_ORDER_FINALIZED, true, Set.of(NotificationChannel.EMAIL), Set.of(Role.ADMIN),
                Set.of(technician.getId()));

        service.notifyConfiguredRecipients(NotificationType.WORK_ORDER_FINALIZED, "T", "M", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> recipients = ArgumentCaptor.forClass(List.class);
        verify(email).send(recipients.capture(), any());
        assertEquals(List.of(admin, technician), recipients.getValue());
        verify(inbox, never()).send(any(), any());
    }

    @Test
    void unTipoApagadoNoSaleNiAlInvolucrado() {
        configure(NotificationType.WORK_ORDER_ASSIGNED, false, Set.of(NotificationChannel.IN_APP), Set.of(), Set.of());

        service.notify(technician.getId(), NotificationType.WORK_ORDER_ASSIGNED, "T", "M", "/");

        verify(inbox, never()).send(any(), any());
    }

    @Test
    void alInvolucradoLeLlegaPorLosCanalesDelTipo() {
        configure(NotificationType.WORK_ORDER_ASSIGNED, true, Set.of(NotificationChannel.IN_APP), Set.of(), Set.of());
        when(userRepository.findById(technician.getId())).thenReturn(Optional.of(technician));

        service.notify(technician.getId(), NotificationType.WORK_ORDER_ASSIGNED, "T", "M", "/");

        verify(inbox).send(List.of(technician), new NotificationContent(NotificationType.WORK_ORDER_ASSIGNED, "T", "M", "/"));
        verify(push, never()).send(any(), any());
    }

    @Test
    void unInvolucradoDesactivadoNoRecibeNada() throws Exception {
        configure(NotificationType.TRIP_OPEN_REMINDER, true, Set.of(NotificationChannel.IN_APP), Set.of(), Set.of());
        User gone = user("ex", Role.CHOFER, false);
        when(userRepository.findById(gone.getId())).thenReturn(Optional.of(gone));

        service.notify(gone.getId(), NotificationType.TRIP_OPEN_REMINDER, "T", "M", "/");

        verify(inbox, never()).send(any(), any());
    }

    @Test
    void losCanalesDeUnPlanMandanAunqueElTipoEsteApagado() {
        configure(NotificationType.MAINTENANCE_DUE_SOON, false, Set.of(NotificationChannel.IN_APP), Set.of(Role.ADMIN), Set.of());

        service.notifyConfiguredRecipients(NotificationType.MAINTENANCE_DUE_SOON, "T", "M", "/vehiculos/1",
                Set.of(NotificationChannel.EMAIL));

        verify(email).send(List.of(admin), new NotificationContent(NotificationType.MAINTENANCE_DUE_SOON, "T", "M", "/vehiculos/1"));
        verify(inbox, never()).send(any(), any());
    }

    @Test
    void sinCanalesDelPlanUsaLaConfiguracionDelTipo() {
        configure(NotificationType.MAINTENANCE_OVERDUE, true, Set.of(NotificationChannel.PUSH), Set.of(Role.ADMIN), Set.of());

        service.notifyConfiguredRecipients(NotificationType.MAINTENANCE_OVERDUE, "T", "M", null, null);

        verify(push).send(any(), any());
    }

    @Test
    void sinDestinatariosNoLlamaANingunCanal() {
        configure(NotificationType.WORK_ORDER_FINALIZED, true, Set.of(NotificationChannel.IN_APP), Set.of(), Set.of());

        service.notifyConfiguredRecipients(NotificationType.WORK_ORDER_FINALIZED, "T", "M", null);

        verify(inbox, never()).send(any(), any());
    }
}
