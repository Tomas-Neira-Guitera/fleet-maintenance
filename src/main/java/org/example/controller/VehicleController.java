package org.example.controller;

import org.example.dto.FleetStatusRowDto;
import org.example.dto.OdometerResultDto;
import org.example.dto.PagedResponse;
import org.example.dto.UpdateOdometerRequest;
import org.example.dto.VehicleSummaryDto;
import org.example.service.VehicleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /vehicles, servido en /api/vehicles -- ver openapi.yaml. El resto del CRUD queda
 * fuera del alcance de CAM-11. La vista de estado de flota (CAM-40) y la carga de
 * kilometraje (feature 5.5) se agregan acá porque operan sobre el mismo recurso vehículo.
 */
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

    /** GET /api/vehicles?view=fleet-status -- CAM-40, ver CAM-40-maintenance-api-contract.md. */
    @GetMapping(params = "view=fleet-status")
    public PagedResponse<FleetStatusRowDto> fleetStatus(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int pageSize,
                                                          @RequestParam(required = false) String status) {
        return vehicleService.getFleetStatus(page, pageSize, status);
    }

    @PatchMapping("/{id}/odometer")
    public OdometerResultDto updateOdometer(@PathVariable String id, @RequestBody UpdateOdometerRequest request) {
        return vehicleService.updateOdometer(id, request.odometerKm());
    }
}
