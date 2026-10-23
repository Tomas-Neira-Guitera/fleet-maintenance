package org.fleetguard.service;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fleetguard.entity.PushSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.apache.http.HttpResponse;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PushGateway con la librería web-push (CAM-119): firma con las claves VAPID de
 * {@code fleetguard.push.*} y cifra el mensaje con las claves de la suscripción (aes128gcm).
 * Sin claves configuradas la push queda apagada: la app levanta igual y no se ofrece activarla.
 */
@Component
public class WebPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(WebPushGateway.class);

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final String publicKey;
    private final PushService pushService;

    public WebPushGateway(@Value("${fleetguard.push.public-key:}") String publicKey,
                          @Value("${fleetguard.push.private-key:}") String privateKey,
                          @Value("${fleetguard.push.subject:mailto:fleetguard@example.com}") String subject) {
        PushService service = null;
        if (!publicKey.isBlank() && !privateKey.isBlank()) {
            // La librería arma las claves con el proveedor de Bouncy Castle, que hay que registrar una vez.
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            try {
                service = new PushService(publicKey, privateKey, subject);
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                // Una clave mal copiada tira IllegalArgumentException desde Bouncy Castle: sin atraparla,
                // la app entera no levantaría por la push.
                log.error("Claves VAPID inválidas en fleetguard.push: la push queda apagada", e);
            }
        } else {
            log.info("Sin claves VAPID en fleetguard.push: la push queda apagada");
        }
        this.pushService = service;
        this.publicKey = service == null ? null : publicKey;
    }

    @Override
    public boolean isEnabled() {
        return pushService != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public int send(PushSubscription subscription, String payload) throws Exception {
        if (pushService == null) {
            throw new IllegalStateException("La push está apagada");
        }
        Notification notification = new Notification(subscription.getEndpoint(), subscription.getP256dh(),
                subscription.getAuth(), payload);
        // send() de la librería espera la respuesta sin límite: un servicio que no contesta colgaría el hilo
        // (y en la prueba, un hilo de Tomcat). Se espera SEND_TIMEOUT; cancelar cierra el cliente HTTP.
        Future<HttpResponse> response = pushService.sendAsync(notification, Encoding.AES128GCM);
        try {
            return response.get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS).getStatusLine().getStatusCode();
        } catch (TimeoutException e) {
            response.cancel(true);
            throw e;
        }
    }
}
