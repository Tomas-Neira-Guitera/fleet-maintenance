package org.fleetguard.controller;

import org.fleetguard.dto.ImportVehiclesRequest;
import org.fleetguard.dto.VehicleImportResultDto;
import org.fleetguard.service.VehicleImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** POST /vehicles/import, servido en /api/vehicles/import (CAM-154). Solo ADMIN, registrado en WebConfig. */
@RestController
@RequestMapping("/vehicles/import")
public class VehicleImportController {

    private final VehicleImportService importService;

    public VehicleImportController(VehicleImportService importService) {
        this.importService = importService;
    }

    /** dryRun=true: la vista previa, sin guardar nada. */
    @PostMapping
    public VehicleImportResultDto importVehicles(@RequestBody ImportVehiclesRequest request,
                                                 @RequestParam(defaultValue = "false") boolean dryRun) {
        return importService.importVehicles(request, dryRun);
    }
}
