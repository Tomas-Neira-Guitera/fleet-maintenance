package org.fleetguard.controller;

import org.fleetguard.dto.DowntimeDto;
import org.fleetguard.service.DowntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /downtime, servido en /api/downtime -- tiempo fuera de servicio y tiempo promedio de reparación (CAM-134).
 * Exige el JWT de un ADMIN (registrado en WebConfig). Ver CAM-134-downtime-contract.md.
 */
@RestController
@RequestMapping("/downtime")
public class DowntimeController {

    private final DowntimeService service;

    public DowntimeController(DowntimeService service) {
        this.service = service;
    }

    @GetMapping
    public DowntimeDto get(@RequestParam(required = false) String from,
                           @RequestParam(required = false) String to,
                           @RequestParam(required = false) String vehicleId) {
        return service.get(from, to, vehicleId);
    }
}
