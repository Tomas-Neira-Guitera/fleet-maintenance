package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.InspectionResultDto;
import org.fleetguard.dto.InspectionSubmissionDto;
import org.fleetguard.service.InspectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/inspections/{vehicleId} -- ver openapi.yaml y CAM-11-dvir-contract.md. Exige el JWT
 * de un CHOFER (CAM-85, registrado en WebConfig): el chofer de la inspección es el del token.
 */
@RestController
@RequestMapping("/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @PostMapping("/{vehicleId}")
    public ResponseEntity<InspectionResultDto> submit(@PathVariable String vehicleId,
                                                        @RequestBody InspectionSubmissionDto submission,
                                                        @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser driver) {
        InspectionResultDto result = inspectionService.submit(vehicleId, submission, driver);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
