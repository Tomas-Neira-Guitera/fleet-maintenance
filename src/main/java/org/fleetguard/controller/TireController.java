package org.fleetguard.controller;

import org.fleetguard.dto.ListResponse;
import org.fleetguard.dto.MountTireRequest;
import org.fleetguard.dto.RetireTireRequest;
import org.fleetguard.dto.RotateTiresRequest;
import org.fleetguard.dto.TireBrandComparisonDto;
import org.fleetguard.dto.TireDetailDto;
import org.fleetguard.dto.TireDto;
import org.fleetguard.dto.TirePositionsDto;
import org.fleetguard.dto.TireRequest;
import org.fleetguard.dto.UnmountTireRequest;
import org.fleetguard.dto.VehicleTiresDto;
import org.fleetguard.service.TireService;
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
 * Neumáticos (CAM-157), ver CAM-157-tires-contract.md. Todo es del admin salvo las posiciones montadas, que
 * también lee el chofer para anotar el dibujo en el pre-viaje. Los roles se registran en WebConfig.
 */
@RestController
public class TireController {

    private final TireService service;

    public TireController(TireService service) {
        this.service = service;
    }

    @GetMapping("/tires")
    public ListResponse<TireDto> list(@RequestParam(required = false) String status) {
        return new ListResponse<>(service.list(status));
    }

    @GetMapping("/tires/brand-comparison")
    public TireBrandComparisonDto brandComparison() {
        return service.brandComparison();
    }

    @GetMapping("/tires/{id}")
    public TireDetailDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/tires")
    public ResponseEntity<TireDto> create(@RequestBody(required = false) TireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/tires/{id}")
    public TireDto update(@PathVariable String id, @RequestBody(required = false) TireRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/tires/{id}/retire")
    public TireDto retire(@PathVariable String id, @RequestBody(required = false) RetireTireRequest request) {
        return service.retire(id, request);
    }

    @DeleteMapping("/tires/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vehicles/{id}/tires")
    public VehicleTiresDto vehicleTires(@PathVariable String id) {
        return service.vehicleTires(id);
    }

    @PostMapping("/vehicles/{id}/tires/mount")
    public VehicleTiresDto mount(@PathVariable String id, @RequestBody(required = false) MountTireRequest request) {
        return service.mount(id, request);
    }

    @PostMapping("/vehicles/{id}/tires/unmount")
    public VehicleTiresDto unmount(@PathVariable String id, @RequestBody(required = false) UnmountTireRequest request) {
        return service.unmount(id, request);
    }

    @PostMapping("/vehicles/{id}/tires/rotate")
    public VehicleTiresDto rotate(@PathVariable String id, @RequestBody(required = false) RotateTiresRequest request) {
        return service.rotate(id, request);
    }

    @GetMapping("/vehicles/{id}/tire-positions")
    public TirePositionsDto positions(@PathVariable String id) {
        return service.positions(id);
    }
}
