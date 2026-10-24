package org.fleetguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.PushConfigDto;
import org.fleetguard.dto.PushSubscriptionRequest;
import org.fleetguard.entity.PushSubscription;
import org.fleetguard.entity.User;
import org.fleetguard.exception.PushValidationException;
import org.fleetguard.repository.PushSubscriptionRepository;
import org.fleetguard.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Push al navegador (CAM-119): guarda en qué navegadores activó cada usuario las push y les manda
 * los avisos. Los envíos son @Async: salen a internet (el servicio de push de Google, Mozilla o
 * Apple) y no tienen que demorar el pedido que originó el aviso. Ver CAM-119-push-contract.md.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    /**
     * Servicios de push de los navegadores: Chrome y Edge sobre Android (FCM), Firefox (Mozilla), Edge en
     * Windows (WNS) y Safari (Apple). Un endpoint tiene que ser de uno de estos dominios o un subdominio.
     */
    private static final List<String> PUSH_SERVICE_DOMAINS = List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com");

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;

    public PushNotificationService(PushSubscriptionRepository subscriptionRepository, UserRepository userRepository,
                                   PushGateway gateway, ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    public PushConfigDto config() {
        return new PushConfigDto(gateway.publicKey());
    }

    /**
     * Guarda la suscripción de este navegador para el usuario. Si el navegador ya estaba suscripto
     * (por él o por otro usuario que usó la misma compu antes), pasa a ser de este usuario.
     */
    @Transactional
    public void subscribe(UUID userId, PushSubscriptionRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        String endpoint = request.endpoint() == null ? "" : request.endpoint().trim();
        if (endpoint.length() > 1000) {
            details.add(new FieldValidationErrorDetail("endpoint", "Hasta 1000 caracteres"));
        } else if (!isKnownPushService(endpoint)) {
            details.add(new FieldValidationErrorDetail("endpoint",
                    "Tiene que ser la URL https de un servicio de push de navegador conocido"));
        }
        PushSubscriptionRequest.Keys keys = request.keys();
        // p256dh: punto P-256 sin comprimir (65 bytes, empieza con 0x04); auth: 16 bytes. Una clave mal
        // formada haría fallar cada envío para siempre.
        byte[] p256dh = keys == null ? null : decodeBase64Url(keys.p256dh());
        if (p256dh == null || p256dh.length != 65 || p256dh[0] != 0x04) {
            details.add(new FieldValidationErrorDetail("keys.p256dh", "Tiene que ser una clave P-256 en base64url"));
        }
        byte[] auth = keys == null ? null : decodeBase64Url(keys.auth());
        if (auth == null || auth.length != 16) {
            details.add(new FieldValidationErrorDetail("keys.auth", "Tiene que ser un secreto de 16 bytes en base64url"));
        }
        if (!details.isEmpty()) {
            throw new PushValidationException("Suscripción inválida", details);
        }

        subscriptionRepository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> existing.renew(userId, keys.p256dh(), keys.auth()),
                () -> subscriptionRepository.save(new PushSubscription(userId, endpoint, keys.p256dh(), keys.auth())));
        log.info("Push activada para el usuario {} en {}…", userId, endpoint.substring(0, Math.min(40, endpoint.length())));
    }

    /**
     * Solo los servicios de push de los navegadores: el backend le hace un POST a esta URL, así que una
     * cualquiera (https://10.0.0.5/...) serviría para hacerle pedidos a hosts internos (SSRF).
     */
    static boolean isKnownPushService(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        return PUSH_SERVICE_DOMAINS.stream()
                .anyMatch(domain -> lowerHost.equals(domain) || lowerHost.endsWith("." + domain));
    }

    private static byte[] decodeBase64Url(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Base64.getUrlDecoder().decode(value.trim().replace("=", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Idempotente: un endpoint que no existe, o que es de otro usuario, no hace nada. */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptionRepository.findByEndpoint(endpoint == null ? "" : endpoint.trim())
                .filter(s -> s.getUserId().equals(userId))
                .ifPresent(subscriptionRepository::delete);
    }

    /** Los destinatarios de un aviso (CAM-117); los desactivados no reciben nada. */
    @Async
    public void sendToUsers(List<UUID> userIds, String title, String message, String link) {
        List<UUID> activeIds = userRepository.findAllById(userIds).stream()
                .filter(User::isActive)
                .map(User::getId)
                .toList();
        if (!activeIds.isEmpty()) {
            deliver(subscriptionRepository.findByUserIdIn(activeIds), title, message, link);
        }
    }

    /**
     * "Mandar una de prueba": una push a todos los navegadores del usuario. A diferencia de los
     * avisos es sincrónica, para poder decirle si llegó: devuelve cuántos servicios de push la
     * aceptaron.
     */
    public int sendTest(UUID userId) {
        return deliver(subscriptionRepository.findByUserId(userId), "FleetMaintenance",
                "Las notificaciones de este dispositivo funcionan.", "/");
    }

    /** Devuelve a cuántos navegadores la aceptó su servicio de push. */
    int deliver(List<PushSubscription> subscriptions, String title, String message, String link) {
        if (!gateway.isEnabled() || subscriptions.isEmpty()) {
            log.info("Push \"{}\" sin enviar: {}", title,
                    gateway.isEnabled() ? "el usuario no tiene navegadores suscriptos" : "la push está apagada");
            return 0;
        }
        String payload = payload(title, message, link);
        int accepted = 0;
        for (PushSubscription subscription : subscriptions) {
            try {
                int status = gateway.send(subscription, payload);
                if (status >= 200 && status < 300) {
                    accepted++;
                } else if (status == 404 || status == 410) {
                    // El usuario revocó el permiso o el navegador venció la suscripción: no va a volver.
                    log.info("Suscripción push {} vencida ({}): se borra", subscription.getId(), status);
                    subscriptionRepository.delete(subscription);
                } else {
                    log.warn("El servicio de push respondió {} para la suscripción {}", status, subscription.getId());
                }
            } catch (Exception e) {
                // Un navegador que falla no frena a los demás.
                log.error("No se pudo mandar la push a la suscripción {}", subscription.getId(), e);
            }
        }
        log.info("Push \"{}\" aceptada por {} de {} navegadores", title, accepted, subscriptions.size());
        return accepted;
    }

    private String payload(String title, String message, String link) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("body", message);
        body.put("link", link);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar el mensaje de la push", e);
        }
    }
}
