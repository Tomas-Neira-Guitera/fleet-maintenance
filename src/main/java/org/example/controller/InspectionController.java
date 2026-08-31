package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.auth.Driver;
import org.example.auth.DriverResolver;
import org.example.dto.InspectionResultDto;
import org.example.dto.InspectionSubmissionDto;
import org.example.service.InspectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/inspections/{vehicleId} -- ver openapi.yaml y CAM-11-dvir-contract.md. */
@RestController
@RequestMapping("/inspections")
public class InspectionController {

    private final InspectionService inspectionService;
    private final DriverResolver driverResolver;

    public InspectionController(InspectionService inspectionService, DriverResolver driverResolver) {
        this.inspectionService = inspectionService;
        this.driverResolver = driverResolver;
    }

    @PostMapping("/{vehicleId}")
    public ResponseEntity<InspectionResultDto> submit(@PathVariable String vehicleId,
                                                        @RequestBody InspectionSubmissionDto submission,
                                                        HttpServletRequest request) {
        Driver driver = driverResolver.resolve(request);
        InspectionResultDto result = inspectionService.submit(vehicleId, submission, driver);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
