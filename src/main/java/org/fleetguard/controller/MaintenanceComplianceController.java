package org.fleetguard.controller;

import org.fleetguard.dto.MaintenanceComplianceDto;
import org.fleetguard.service.MaintenanceComplianceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /maintenance-compliance, servido en /api/maintenance-compliance -- indicadores de cumplimiento del
 * mantenimiento (CAM-155). Exige el JWT de un ADMIN (registrado en WebConfig).
 */
@RestController
@RequestMapping("/maintenance-compliance")
public class MaintenanceComplianceController {

    private final MaintenanceComplianceService service;

    public MaintenanceComplianceController(MaintenanceComplianceService service) {
        this.service = service;
    }

    @GetMapping
    public MaintenanceComplianceDto get(@RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to) {
        return service.get(from, to);
    }
}
