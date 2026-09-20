package org.example.controller;

import org.example.dto.CreateVehicleRequest;
import org.example.dto.FleetStatusRowDto;
import org.example.dto.OdometerResultDto;
import org.example.dto.PagedResponse;
import org.example.dto.UpdateOdometerRequest;
import org.example.dto.UpdateVehicleRequest;
import org.example.dto.VehicleHistoryDto;
import org.example.dto.VehicleSummaryDto;
import org.example.service.VehicleHistoryService;
import org.example.service.VehicleService;
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

import java.util.List;

/**
 * /vehicles, servido en /api/vehicles -- ver openapi.yaml. El ABM (alta/edición/baja,
 * CAM-25) se agrega acá porque opera sobre el mismo recurso vehículo que ya exponía
 * CAM-11 (listado) y CAM-40 (estado de flota, carga de kilometraje).
 */
@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleHistoryService vehicleHistoryService;

    public VehicleController(VehicleService vehicleService, VehicleHistoryService vehicleHistoryService) {
        this.vehicleService = vehicleService;
        this.vehicleHistoryService = vehicleHistoryService;
    }

    /** GET /api/vehicles/{id} -- CAM-22. */
    @GetMapping("/{id}")
    public VehicleSummaryDto getVehicle(@PathVariable String id) {
        return vehicleService.getById(id);
    }

    /** GET /api/vehicles/{id}/history -- CAM-22, ver CAM-22-vehicle-history-contract.md. */
    @GetMapping("/{id}/history")
    public VehicleHistoryDto getVehicleHistory(@PathVariable String id) {
        return vehicleHistoryService.getHistory(id);
    }

    @GetMapping
    public List<VehicleSummaryDto> listVehicles(@RequestParam(defaultValue = "true") boolean active) {
        return vehicleService.listVehicles(active);
    }

    /** GET /api/vehicles?view=fleet-status -- CAM-40, ver CAM-40-maintenance-api-contract.md. */
    @GetMapping(params = "view=fleet-status")
    public PagedResponse<FleetStatusRowDto> fleetStatus(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int pageSize,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "true") boolean active) {
        return vehicleService.getFleetStatus(page, pageSize, status, active);
    }

    @PatchMapping("/{id}/odometer")
    public OdometerResultDto updateOdometer(@PathVariable String id, @RequestBody UpdateOdometerRequest request) {
        return vehicleService.updateOdometer(id, request.odometerKm());
    }

    @PostMapping
    public ResponseEntity<VehicleSummaryDto> createVehicle(@RequestBody CreateVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(request));
    }

    @PatchMapping("/{id}")
    public VehicleSummaryDto updateVehicle(@PathVariable String id, @RequestBody UpdateVehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateVehicle(@PathVariable String id) {
        vehicleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
