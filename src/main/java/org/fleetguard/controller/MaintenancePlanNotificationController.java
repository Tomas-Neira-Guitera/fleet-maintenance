package org.fleetguard.controller;

import org.fleetguard.dto.PlanNotificationSettingsDto;
import org.fleetguard.dto.UpdatePlanNotificationSettingsRequest;
import org.fleetguard.service.PlanNotificationSettingsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/maintenance-plans/{id}/notifications -- avisos propios de un plan (CAM-118), solo ADMIN. */
@RestController
@RequestMapping("/maintenance-plans/{planId}/notifications")
public class MaintenancePlanNotificationController {

    private final PlanNotificationSettingsService service;

    public MaintenancePlanNotificationController(PlanNotificationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public PlanNotificationSettingsDto get(@PathVariable String planId) {
        return service.get(planId);
    }

    @PutMapping
    public PlanNotificationSettingsDto update(@PathVariable String planId,
                                              @RequestBody UpdatePlanNotificationSettingsRequest request) {
        return service.update(planId, request);
    }

    /** El plan vuelve a la configuración general. */
    @DeleteMapping
    public PlanNotificationSettingsDto reset(@PathVariable String planId) {
        return service.reset(planId);
    }
}
