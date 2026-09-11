package org.example.controller;

import org.example.dto.CreateScheduleRequest;
import org.example.dto.ListResponse;
import org.example.dto.ScheduleDto;
import org.example.dto.UpdateScheduleRequest;
import org.example.service.ScheduledMaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint genérico para programar mantenimientos, alimenta el calendario semanal del
 * Resumen (CAM-42) y los botones de "planificar" de CAM-50/CAM-51. No cuelga de
 * /vehicles ni de /defects -- ver CAM-42-programacion-mantenimientos.md.
 */
@RestController
@RequestMapping("/maintenance-schedule")
public class ScheduledMaintenanceController {

    private final ScheduledMaintenanceService service;

    public ScheduledMaintenanceController(ScheduledMaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ScheduleDto> create(@RequestBody CreateScheduleRequest request) {
        ScheduledMaintenanceService.CreateResult result = service.create(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.dto());
    }

    @PatchMapping("/{id}")
    public ScheduleDto update(@PathVariable String id, @RequestBody UpdateScheduleRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    public ListResponse<ScheduleDto> list(@RequestParam String from,
                                           @RequestParam String to,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String vehicleId) {
        return new ListResponse<>(service.listByRange(from, to, status, vehicleId));
    }
}
