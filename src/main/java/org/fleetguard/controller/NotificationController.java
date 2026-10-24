package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.ListResponse;
import org.fleetguard.dto.NotificationDto;
import org.fleetguard.service.NotificationInboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** /api/notifications -- la campanita (CAM-96), cualquier rol. Ver CAM-96-notifications-contract.md. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationInboxService service;

    public NotificationController(NotificationInboxService service) {
        this.service = service;
    }

    @GetMapping
    public ListResponse<NotificationDto> list(@RequestParam(required = false) Integer limit,
                                              @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        return new ListResponse<>(service.list(user.id(), limit));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        return Map.of("count", service.unreadCount(user.id()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id,
                                         @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        service.markRead(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        service.markAllRead(user.id());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String id,
                                        @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        service.dismiss(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
