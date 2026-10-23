package org.fleetguard.dto;

/**
 * Body de POST /api/push/subscriptions (CAM-119): lo mismo que da {@code PushSubscription.toJSON()}
 * en el navegador, así el front lo manda tal cual. {@code expirationTime} se ignora.
 */
public record PushSubscriptionRequest(String endpoint, Keys keys) {

    public record Keys(String p256dh, String auth) {
    }
}
