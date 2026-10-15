package org.fleetguard.controller;

import org.fleetguard.dto.VehicleCostsDto;
import org.fleetguard.service.VehicleCostsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /vehicles/{id}/costs, servido en /api/vehicles/{id}/costs -- costo de mantenimiento de un
 * vehículo (CAM-105). Aparte de VehicleController porque, a diferencia del resto de /vehicles,
 * exige el JWT de un ADMIN (registrado en WebConfig). Ver CAM-105-vehicle-costs-contract.md.
 */
@RestController
@RequestMapping("/vehicles")
public class VehicleCostsController {

    private final VehicleCostsService service;

    public VehicleCostsController(VehicleCostsService service) {
        this.service = service;
    }

    @GetMapping("/{id}/costs")
    public VehicleCostsDto getCosts(@PathVariable String id) {
        return service.getCosts(id);
    }
}
