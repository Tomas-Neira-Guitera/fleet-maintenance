package org.fleetguard.controller;

import org.fleetguard.dto.CreateMaintenancePlanRequest;
import org.fleetguard.dto.ListResponse;
import org.fleetguard.dto.MaintenancePlanDto;
import org.fleetguard.dto.UpdateMaintenancePlanRequest;
import org.fleetguard.service.MaintenancePlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de planes de mantenimiento -- /api/maintenance-plans, ver CAM-40-maintenance-api-contract.md. */
@RestController
@RequestMapping("/maintenance-plans")
public class MaintenancePlanController {

    private final MaintenancePlanService service;

    public MaintenancePlanController(MaintenancePlanService service) {
        this.service = service;
    }

    @GetMapping
    public ListResponse<MaintenancePlanDto> list(@RequestParam(defaultValue = "true") boolean active,
                                                   @RequestParam(required = false) String category) {
        return new ListResponse<>(service.list(active, category));
    }

    @PostMapping
    public ResponseEntity<MaintenancePlanDto> create(@RequestBody CreateMaintenancePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    public MaintenancePlanDto update(@PathVariable String id, @RequestBody UpdateMaintenancePlanRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
