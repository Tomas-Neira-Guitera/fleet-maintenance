package org.fleetguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fleetguard.dto.PushSubscriptionRequest;
import org.fleetguard.entity.PushSubscription;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.exception.PushValidationException;
import org.fleetguard.repository.PushSubscriptionRepository;
import org.fleetguard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-119 -- suscripciones y envío de push al navegador, con el servicio de push simulado. */
class PushNotificationServiceTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PushGateway gateway = mock(PushGateway.class);
    private final PushNotificationService service =
            new PushNotificationService(repository, userRepository, gateway, new ObjectMapper());

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.publicKey()).thenReturn("BKxl");
        when(repository.findByEndpoint(any())).thenReturn(Optional.empty());
    }

    private static PushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys(p256dh, auth));
    }

    @Test
    void laConfigDevuelveLaClavePublicaONullSiLaPushEstaApagada() {
        assertEquals("BKxl", service.config().publicKey());

        when(gateway.publicKey()).thenReturn(null);
        assertNull(service.config().publicKey());
    }

    @Test
    void suscribirGuardaUnNavegadorNuevo() {
        service.subscribe(userId, request(ENDPOINT, "p256", "auth"));

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(ENDPOINT, saved.getValue().getEndpoint());
        assertEquals("p256", saved.getValue().getP256dh());
        assertEquals("auth", saved.getValue().getAuth());
    }

    @Test
    void elMismoNavegadorConOtroUsuarioPasaASerDelNuevo() {
        PushSubscription existing = new PushSubscription(UUID.randomUUID(), ENDPOINT, "vieja", "vieja");
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));

        service.subscribe(userId, request(ENDPOINT, "nueva", "nueva"));

        assertEquals(userId, existing.getUserId());
        assertEquals("nueva", existing.getP256dh());
        verify(repository, never()).save(any());
    }

    @Test
    void unaSuscripcionSinEndpointHttpsOSinClavesDevuelve422() {
        PushValidationException ex = assertThrows(PushValidationException.class,
                () -> service.subscribe(userId, new PushSubscriptionRequest("http://otro", null)));

        assertEquals(List.of("endpoint", "keys.p256dh", "keys.auth"),
                ex.getDetails().stream().map(d -> d.field()).toList());
        assertThrows(PushValidationException.class,
                () -> service.subscribe(userId, request("https://" + "x".repeat(1000), "p", "a")));
    }

    @Test
    void darseDeBajaSoloBorraLaSuscripcionPropia() {
        PushSubscription mine = new PushSubscription(userId, ENDPOINT, "p", "a");
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(mine));
        service.unsubscribe(userId, ENDPOINT);
        verify(repository).delete(mine);

        PushSubscription other = new PushSubscription(UUID.randomUUID(), "https://otro", "p", "a");
        when(repository.findByEndpoint("https://otro")).thenReturn(Optional.of(other));
        service.unsubscribe(userId, "https://otro");
        verify(repository, never()).delete(other);
    }

    @Test
    void mandaElAvisoATodosLosNavegadoresDelUsuario() throws Exception {
        PushSubscription laptop = new PushSubscription(userId, ENDPOINT, "p", "a");
        PushSubscription phone = new PushSubscription(userId, "https://updates.push.services.mozilla.com/x", "p", "a");
        when(repository.findByUserId(userId)).thenReturn(List.of(laptop, phone));
        when(gateway.send(any(), anyString())).thenReturn(201);

        service.sendToUser(userId, "Te asignaron una orden de trabajo", "Frenos (AB123CD).", "/");

        String payload = "{\"title\":\"Te asignaron una orden de trabajo\",\"body\":\"Frenos (AB123CD).\",\"link\":\"/\"}";
        verify(gateway).send(laptop, payload);
        verify(gateway).send(phone, payload);
    }

    @Test
    void unaSuscripcionVencidaSeBorraYUnErrorNoFrenaALasDemas() throws Exception {
        PushSubscription gone = new PushSubscription(userId, "https://gone", "p", "a");
        PushSubscription broken = new PushSubscription(userId, "https://broken", "p", "a");
        PushSubscription ok = new PushSubscription(userId, ENDPOINT, "p", "a");
        PushSubscription rejected = new PushSubscription(userId, "https://rejected", "p", "a");
        when(gateway.send(eq(gone), anyString())).thenReturn(410);
        when(gateway.send(eq(broken), anyString())).thenThrow(new java.io.IOException("sin red"));
        when(gateway.send(eq(ok), anyString())).thenReturn(201);
        when(gateway.send(eq(rejected), anyString())).thenReturn(400);
        when(repository.findByUserId(userId)).thenReturn(List.of(gone, broken, ok, rejected));

        assertEquals(1, service.sendTest(userId));

        verify(repository).delete(gone);
        verify(repository, never()).delete(broken);
        verify(repository, never()).delete(rejected);
    }

    @Test
    void conLaPushApagadaNoMandaNada() throws Exception {
        when(gateway.isEnabled()).thenReturn(false);
        when(repository.findByUserId(userId)).thenReturn(List.of(new PushSubscription(userId, ENDPOINT, "p", "a")));

        assertEquals(0, service.sendTest(userId));

        verify(gateway, never()).send(any(), any());
    }

    @Test
    void aLosAdminsSoloLesLlegaALosActivos() throws Exception {
        User active = admin(true);
        User inactive = admin(false);
        when(userRepository.findByRoleOrderByUsernameAsc(Role.ADMIN)).thenReturn(List.of(active, inactive));
        PushSubscription subscription = new PushSubscription(active.getId(), ENDPOINT, "p", "a");
        when(repository.findByUserIdIn(List.of(active.getId()))).thenReturn(List.of(subscription));
        when(gateway.send(any(), anyString())).thenReturn(201);

        service.sendToAdmins("Defecto bloqueante", "AB123CD", "/vehiculos/1");

        verify(gateway).send(eq(subscription), anyString());
    }

    @Test
    void sinAdminsActivosNoBuscaSuscripciones() {
        when(userRepository.findByRoleOrderByUsernameAsc(Role.ADMIN)).thenReturn(List.of(admin(false)));

        service.sendToAdmins("Defecto bloqueante", "AB123CD", "/vehiculos/1");

        verify(repository, never()).findByUserIdIn(any());
    }

    private static User admin(boolean active) {
        User user = new User("admin-" + active, "hash", Role.ADMIN);
        try {
            Field id = User.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(user, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        user.setActive(active);
        return user;
    }
}
