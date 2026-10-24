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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    // Una clave P-256 y un secreto de 16 bytes reales, como los que da el navegador.
    private static final String P256DH =
            "BNHx2hoGn4YMkkYurf__YgP0pLciSwI4t5gXoedDT6y0WiTulkEXVrBuzfjUNkOsVIRZ-VqE2fuYUISpncchR00";
    private static final String AUTH = "WeWhPchfgEPGpzPcxjoH-Q";

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
        service.subscribe(userId, request(ENDPOINT, P256DH, AUTH));

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(ENDPOINT, saved.getValue().getEndpoint());
        assertEquals(P256DH, saved.getValue().getP256dh());
        assertEquals(AUTH, saved.getValue().getAuth());
    }

    @Test
    void elMismoNavegadorConOtroUsuarioPasaASerDelNuevo() {
        PushSubscription existing = new PushSubscription(UUID.randomUUID(), ENDPOINT, "vieja", "vieja");
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));

        service.subscribe(userId, request(ENDPOINT, P256DH, AUTH));

        assertEquals(userId, existing.getUserId());
        assertEquals(P256DH, existing.getP256dh());
        verify(repository, never()).save(any());
    }

    @Test
    void unaSuscripcionSinEndpointConocidoOConClavesMalFormadasDevuelve422() {
        PushValidationException ex = assertThrows(PushValidationException.class,
                () -> service.subscribe(userId, new PushSubscriptionRequest("http://otro", null)));
        assertEquals(List.of("endpoint", "keys.p256dh", "keys.auth"),
                ex.getDetails().stream().map(d -> d.field()).toList());

        PushValidationException badKeys = assertThrows(PushValidationException.class,
                () -> service.subscribe(userId, request(ENDPOINT, AUTH, "no es base64url!")));
        assertEquals(List.of("keys.p256dh", "keys.auth"), badKeys.getDetails().stream().map(d -> d.field()).toList());

        assertThrows(PushValidationException.class,
                () -> service.subscribe(userId, request("https://fcm.googleapis.com/" + "x".repeat(1000), P256DH, AUTH)));
    }

    @Test
    void soloSeAceptanLosServiciosDePushDeLosNavegadores() {
        assertTrue(PushNotificationService.isKnownPushService("https://fcm.googleapis.com/fcm/send/abc"));
        assertTrue(PushNotificationService.isKnownPushService("https://updates.push.services.mozilla.com/wpush/v2/x"));
        assertTrue(PushNotificationService.isKnownPushService("https://wns2-bn3p.notify.windows.com/w/?token=x"));
        assertTrue(PushNotificationService.isKnownPushService("https://web.push.apple.com/abc"));
        assertTrue(PushNotificationService.isKnownPushService("https://fcm.googleapis.com:443/fcm/send/abc"));

        // Hosts internos, otro protocolo u otro puerto, usuario en la URL o un dominio parecido: no.
        assertFalse(PushNotificationService.isKnownPushService("https://10.0.0.5:8443/loquesea"));
        assertFalse(PushNotificationService.isKnownPushService("http://fcm.googleapis.com/fcm/send/abc"));
        assertFalse(PushNotificationService.isKnownPushService("https://fcm.googleapis.com:8443/x"));
        assertFalse(PushNotificationService.isKnownPushService("https://fcm.googleapis.com@10.0.0.5/x"));
        assertFalse(PushNotificationService.isKnownPushService("https://evilfcm.googleapis.com.attacker.com/x"));
        assertFalse(PushNotificationService.isKnownPushService("https://notfcm.googleapis.com.evil/x"));
        assertFalse(PushNotificationService.isKnownPushService("no es una url"));
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
        User technician = user(Role.TECNICO, true);
        when(userRepository.findAllById(List.of(userId))).thenReturn(List.of(technician));
        when(repository.findByUserIdIn(List.of(technician.getId()))).thenReturn(List.of(laptop, phone));
        when(gateway.send(any(), anyString())).thenReturn(201);

        service.sendToUsers(List.of(userId), "Te asignaron una orden de trabajo", "Frenos (AB123CD).", "/");

        String payload = "{\"title\":\"Te asignaron una orden de trabajo\",\"body\":\"Frenos (AB123CD).\",\"link\":\"/\"}";
        verify(gateway).send(laptop, payload);
        verify(gateway).send(phone, payload);
    }

    @Test
    void aUnUsuarioDesactivadoONoEncontradoNoLeMandaNada() throws Exception {
        when(userRepository.findAllById(List.of(userId))).thenReturn(List.of(user(Role.TECNICO, false)));
        service.sendToUsers(List.of(userId), "Título", "Mensaje", "/");

        when(userRepository.findAllById(List.of(userId))).thenReturn(List.of());
        service.sendToUsers(List.of(userId), "Título", "Mensaje", "/");

        verify(repository, never()).findByUserIdIn(any());
        verify(gateway, never()).send(any(), any());
    }

    @Test
    void unaSuscripcionVencidaSeBorraYUnErrorNoFrenaALasDemas() throws Exception {
        PushSubscription gone = new PushSubscription(userId, "https://gone", "p", "a");
        PushSubscription broken = new PushSubscription(userId, "https://broken", "p", "a");
        PushSubscription ok = new PushSubscription(userId, ENDPOINT, "p", "a");
        PushSubscription rejected = new PushSubscription(userId, "https://rejected", "p", "a");
        PushSubscription redirected = new PushSubscription(userId, "https://redirected", "p", "a");
        when(gateway.send(eq(gone), anyString())).thenReturn(410);
        when(gateway.send(eq(broken), anyString())).thenThrow(new java.io.IOException("sin red"));
        when(gateway.send(eq(ok), anyString())).thenReturn(201);
        when(gateway.send(eq(rejected), anyString())).thenReturn(400);
        when(gateway.send(eq(redirected), anyString())).thenReturn(302);
        when(repository.findByUserId(userId)).thenReturn(List.of(gone, broken, ok, rejected, redirected));

        // Solo cuenta un 2xx: una redirección no es "entregada".
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
    void aVariosDestinatariosSoloLesLlegaALosActivos() throws Exception {
        User active = user(Role.ADMIN, true);
        User inactive = user(Role.ADMIN, false);
        List<UUID> ids = List.of(active.getId(), inactive.getId());
        when(userRepository.findAllById(ids)).thenReturn(List.of(active, inactive));
        PushSubscription subscription = new PushSubscription(active.getId(), ENDPOINT, "p", "a");
        when(repository.findByUserIdIn(List.of(active.getId()))).thenReturn(List.of(subscription));
        when(gateway.send(any(), anyString())).thenReturn(201);

        service.sendToUsers(ids, "Defecto bloqueante", "AB123CD", "/vehiculos/1");

        verify(gateway).send(eq(subscription), anyString());
    }

    private static User user(Role role, boolean active) {
        User user = new User(role + "-" + active, "hash", role);
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
