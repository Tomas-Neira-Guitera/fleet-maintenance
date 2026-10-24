package org.fleetguard.controller;

import org.fleetguard.dto.ListResponse;
import org.fleetguard.dto.NotificationSettingDto;
import org.fleetguard.dto.UpdateNotificationSettingRequest;
import org.fleetguard.service.NotificationSettingsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/notification-settings -- configuración de avisos (CAM-117), solo ADMIN. */
@RestController
@RequestMapping("/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService service;

    public NotificationSettingsController(NotificationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ListResponse<NotificationSettingDto> list() {
        return new ListResponse<>(service.list());
    }

    @PutMapping("/{type}")
    public NotificationSettingDto update(@PathVariable String type, @RequestBody UpdateNotificationSettingRequest request) {
        return service.update(type, request);
    }

    /** Vuelve al default del catálogo. */
    @DeleteMapping("/{type}")
    public NotificationSettingDto reset(@PathVariable String type) {
        return service.reset(type);
    }
}
