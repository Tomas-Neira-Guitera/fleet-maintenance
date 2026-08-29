package org.example.controller;

import org.example.dto.VehicleSummaryDto;
import org.example.service.VehicleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /vehicles (served at /api/vehicles via server.servlet.context-path) -- see openapi.yaml. The rest of the vehicle CRUD belongs to fleet management, out of CAM-11's scope. */
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
