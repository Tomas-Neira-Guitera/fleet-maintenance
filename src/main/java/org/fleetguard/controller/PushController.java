package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.PushConfigDto;
import org.fleetguard.dto.PushSubscriptionRequest;
import org.fleetguard.service.PushNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * /push, servido en /api/push -- push al navegador (CAM-119). Cualquier rol, con su JWT (registrado
 * en WebConfig): cada usuario maneja solo sus propias suscripciones. Ver CAM-119-push-contract.md.
 */
@RestController
@RequestMapping("/push")
public class PushController {

    private final PushNotificationService service;

    public PushController(PushNotificationService service) {
        this.service = service;
    }

    @GetMapping("/public-key")
    public PushConfigDto publicKey() {
        return service.config();
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@RequestBody PushSubscriptionRequest request,
                                          @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        service.subscribe(user.id(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint,
                                            @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        service.unsubscribe(user.id(), endpoint);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public Map<String, Integer> test(@RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        return Map.of("delivered", service.sendTest(user.id()));
    }
}
