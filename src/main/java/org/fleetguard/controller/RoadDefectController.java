package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.DefectDto;
import org.fleetguard.dto.RoadDefectRequest;
import org.fleetguard.service.RoadDefectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/vehicles/{id}/road-defects -- CAM-139, ver CAM-139-road-defect-contract.md. Exige el JWT de un
 * CHOFER (registrado en WebConfig): el que reporta tiene que ser el chofer del viaje abierto.
 */
@RestController
public class RoadDefectController {

    private final RoadDefectService roadDefectService;

    public RoadDefectController(RoadDefectService roadDefectService) {
        this.roadDefectService = roadDefectService;
    }

    @PostMapping("/vehicles/{id}/road-defects")
    public ResponseEntity<DefectDto> report(@PathVariable String id, @RequestBody(required = false) RoadDefectRequest request,
                                            @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser driver) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roadDefectService.report(id, request, driver));
    }
}
