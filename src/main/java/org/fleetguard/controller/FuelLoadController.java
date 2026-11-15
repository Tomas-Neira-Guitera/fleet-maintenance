package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.FuelLoadDto;
import org.fleetguard.dto.FuelLoadRequest;
import org.fleetguard.dto.VehicleFuelDto;
import org.fleetguard.service.FuelLoadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Combustible (CAM-156), ver CAM-156-fuel-contract.md. Cargar: ADMIN o CHOFER; editar, borrar y ver el
 * consumo del vehículo: solo ADMIN. Los roles se registran en WebConfig.
 */
@RestController
public class FuelLoadController {

    private final FuelLoadService service;

    public FuelLoadController(FuelLoadService service) {
        this.service = service;
    }

    @PostMapping("/fuel-loads")
    public ResponseEntity<FuelLoadDto> create(@RequestBody(required = false) FuelLoadRequest request,
                                              @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, user));
    }

    @PutMapping("/fuel-loads/{id}")
    public FuelLoadDto update(@PathVariable String id, @RequestBody(required = false) FuelLoadRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/fuel-loads/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vehicles/{id}/fuel")
    public VehicleFuelDto vehicleFuel(@PathVariable String id, @RequestParam(required = false) Integer months) {
        return service.vehicleFuel(id, months);
    }
}
