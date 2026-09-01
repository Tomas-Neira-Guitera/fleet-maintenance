package org.example.controller;

import org.example.dto.VehicleSummaryDto;
import org.example.service.VehicleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /vehicles, servido en /api/vehicles -- ver openapi.yaml. El resto del CRUD queda fuera del alcance de CAM-11. */
@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleSummaryDto> listVehicles() {
        return vehicleService.listVehicles();
    }
}
