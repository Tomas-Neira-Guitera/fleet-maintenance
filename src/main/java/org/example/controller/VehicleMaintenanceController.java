package org.example.controller;

import org.example.dto.AssignmentDto;
import org.example.dto.CompletionDto;
import org.example.dto.CompletionResultDto;
import org.example.dto.CreateAssignmentRequest;
import org.example.dto.CreateCompletionRequest;
import org.example.dto.ListResponse;
import org.example.dto.UpdateAssignmentRequest;
import org.example.service.MaintenanceCompletionService;
import org.example.service.VehicleMaintenanceAssignmentService;
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

/**
 * Todo lo que opera sobre la relación vehículo↔plan vive acá, anidado bajo el vehículo --
 * no hay un recurso "assignments" a nivel raíz (ver CAM-40-maintenance-api-contract.md,
 * división de recursos).
 */
@RestController
@RequestMapping("/vehicles/{vehicleId}/maintenance-assignments")
public class VehicleMaintenanceController {

    private final VehicleMaintenanceAssignmentService assignmentService;
    private final MaintenanceCompletionService completionService;

    public VehicleMaintenanceController(VehicleMaintenanceAssignmentService assignmentService,
                                         MaintenanceCompletionService completionService) {
        this.assignmentService = assignmentService;
        this.completionService = completionService;
    }

    @GetMapping
    public ListResponse<AssignmentDto> list(@PathVariable String vehicleId,
                                              @RequestParam(defaultValue = "true") boolean active) {
        return new ListResponse<>(assignmentService.list(vehicleId, active));
    }

    @PostMapping
    public ResponseEntity<AssignmentDto> create(@PathVariable String vehicleId,
                                                  @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.create(vehicleId, request));
    }

    @PatchMapping("/{assignmentId}")
    public AssignmentDto update(@PathVariable String vehicleId, @PathVariable String assignmentId,
                                  @RequestBody UpdateAssignmentRequest request) {
        return assignmentService.update(vehicleId, assignmentId, request);
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> delete(@PathVariable String vehicleId, @PathVariable String assignmentId) {
        assignmentService.delete(vehicleId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{assignmentId}/completions")
    public ListResponse<CompletionDto> listCompletions(@PathVariable String vehicleId, @PathVariable String assignmentId) {
        return new ListResponse<>(completionService.list(vehicleId, assignmentId));
    }

    @PostMapping("/{assignmentId}/completions")
    public ResponseEntity<CompletionResultDto> createCompletion(@PathVariable String vehicleId, @PathVariable String assignmentId,
                                                                   @RequestBody CreateCompletionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(completionService.create(vehicleId, assignmentId, request));
    }
}
