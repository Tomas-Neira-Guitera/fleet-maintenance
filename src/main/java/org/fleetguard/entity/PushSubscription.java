package org.fleetguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un navegador donde un usuario activó las push (CAM-119). Lo que devuelve
 * {@code PushManager.subscribe()} en el front: la URL del servicio de push de ese navegador
 * (endpoint) y las claves con que se cifra cada mensaje (p256dh, auth). Un usuario puede tener
 * varias (la compu y el celular); un endpoint es de un solo usuario a la vez.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Las URLs de FCM y Mozilla rondan los 200 caracteres; 1000 deja margen sin ser TEXT.
    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PushSubscription() {
        // JPA
    }

    public PushSubscription(UUID userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = Instant.now();
    }

    /** El mismo navegador suscripto de nuevo, quizás por otro usuario que entró después. */
    public void renew(UUID userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }
}
