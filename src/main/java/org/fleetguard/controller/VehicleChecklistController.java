package org.fleetguard.controller;

import org.fleetguard.dto.ChecklistItemDto;
import org.fleetguard.dto.CreateVehicleChecklistItemRequest;
import org.fleetguard.dto.ListResponse;
import org.fleetguard.dto.UpdateVehicleChecklistItemRequest;
import org.fleetguard.dto.VehicleChecklistItemDto;
import org.fleetguard.service.VehicleChecklistService;
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

/** Checklist DVIR configurable por vehículo (CAM-31) -- ver docs/api/CAM-31-vehicle-checklist-contract.md. */
@RestController
@RequestMapping("/vehicles/{vehicleId}")
public class VehicleChecklistController {

    private final VehicleChecklistService service;

    public VehicleChecklistController(VehicleChecklistService service) {
        this.service = service;
    }

    @GetMapping("/checklist")
    public ListResponse<ChecklistItemDto> checklist(@PathVariable String vehicleId,
                                                    @RequestParam(required = false) String type) {
        return new ListResponse<>(service.getChecklist(vehicleId, type));
    }

    @GetMapping("/checklist-items")
    public ListResponse<VehicleChecklistItemDto> config(@PathVariable String vehicleId) {
        return new ListResponse<>(service.getConfig(vehicleId));
    }

    @PostMapping("/checklist-items")
    public ResponseEntity<VehicleChecklistItemDto> addItem(@PathVariable String vehicleId,
                                                           @RequestBody CreateVehicleChecklistItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addExtra(vehicleId, request));
    }

    @PatchMapping("/checklist-items/{itemId}")
    public VehicleChecklistItemDto updateItem(@PathVariable String vehicleId, @PathVariable String itemId,
                                              @RequestBody UpdateVehicleChecklistItemRequest request) {
        return service.setEnabled(vehicleId, itemId, request);
    }

    @DeleteMapping("/checklist-items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable String vehicleId, @PathVariable String itemId) {
        service.deleteExtra(vehicleId, itemId);
        return ResponseEntity.noContent().build();
    }
}
