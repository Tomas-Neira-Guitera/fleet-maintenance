package org.fleetguard.controller;

import org.fleetguard.dto.VehicleComparisonDto;
import org.fleetguard.service.VehicleComparisonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/vehicles/comparison (CAM-140), solo el admin -- ver CAM-140-vehicle-comparison-contract.md. */
@RestController
@RequestMapping("/vehicles/comparison")
public class VehicleComparisonController {

    private final VehicleComparisonService service;

    public VehicleComparisonController(VehicleComparisonService service) {
        this.service = service;
    }

    @GetMapping
    public VehicleComparisonDto compare(@RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to) {
        return service.compare(from, to);
    }
}
