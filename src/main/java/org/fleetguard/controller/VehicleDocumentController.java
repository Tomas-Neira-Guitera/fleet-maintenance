package org.fleetguard.controller;

import org.fleetguard.dto.CreateVehicleDocumentRequest;
import org.fleetguard.dto.RenewVehicleDocumentRequest;
import org.fleetguard.dto.UpdateVehicleDocumentRequest;
import org.fleetguard.dto.VehicleDocumentDto;
import org.fleetguard.dto.VehicleDocumentsDto;
import org.fleetguard.service.VehicleDocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /vehicles/{vehicleId}/documents, servido en /api -- documentación del vehículo (CAM-127). Solo el admin
 * (JwtAuthInterceptor, en WebConfig). Ver docs/api/CAM-127-vehicle-documents-contract.md.
 */
@RestController
@RequestMapping("/vehicles/{vehicleId}/documents")
public class VehicleDocumentController {

    private final VehicleDocumentService service;

    public VehicleDocumentController(VehicleDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public VehicleDocumentsDto list(@PathVariable String vehicleId) {
        return service.list(vehicleId);
    }

    @PostMapping
    public ResponseEntity<VehicleDocumentDto> create(@PathVariable String vehicleId,
                                                     @RequestBody CreateVehicleDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(vehicleId, request));
    }

    @PostMapping("/{documentId}/renewals")
    public ResponseEntity<VehicleDocumentDto> renew(@PathVariable String vehicleId, @PathVariable String documentId,
                                                    @RequestBody RenewVehicleDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.renew(vehicleId, documentId, request));
    }

    @PatchMapping("/{documentId}")
    public VehicleDocumentDto update(@PathVariable String vehicleId, @PathVariable String documentId,
                                     @RequestBody UpdateVehicleDocumentRequest request) {
        return service.update(vehicleId, documentId, request);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String vehicleId, @PathVariable String documentId) {
        service.delete(vehicleId, documentId);
        return ResponseEntity.noContent().build();
    }
}
