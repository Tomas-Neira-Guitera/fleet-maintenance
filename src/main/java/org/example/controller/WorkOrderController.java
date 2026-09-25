package org.example.controller;

import org.example.dto.CreateWorkOrderExpenseRequest;
import org.example.dto.CreateWorkOrderPhotoRequest;
import org.example.dto.CreateWorkOrderRequest;
import org.example.dto.ListResponse;
import org.example.dto.UpdateWorkOrderRequest;
import org.example.dto.WorkOrderDto;
import org.example.dto.WorkOrderExpenseDto;
import org.example.dto.WorkOrderPhotoDto;
import org.example.service.WorkOrderService;
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
 * /work-orders, servido en /api/work-orders -- ver claude/CAM-14-ordenes-de-trabajo.md en
 * el Project (CAM-14/CAM-15/CAM-62/CAM-63). Recurso propio, no anidado bajo /vehicles --
 * mismo criterio que /api/defects y /api/maintenance-schedule.
 */
@RestController
@RequestMapping("/work-orders")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WorkOrderDto> create(@RequestBody CreateWorkOrderRequest request) {
        WorkOrderService.CreateResult result = service.create(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.dto());
    }

    @GetMapping
    public ListResponse<WorkOrderDto> list(@RequestParam(required = false) String vehicleId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String executionType,
                                            @RequestParam(required = false) String technicianId) {
        return new ListResponse<>(service.list(vehicleId, status, executionType, technicianId));
    }

    @GetMapping("/{id}")
    public WorkOrderDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public WorkOrderDto update(@PathVariable String id, @RequestBody UpdateWorkOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/expenses")
    public ResponseEntity<WorkOrderExpenseDto> addExpense(@PathVariable String id,
                                                            @RequestBody CreateWorkOrderExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addExpense(id, request));
    }

    @DeleteMapping("/{id}/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable String id, @PathVariable String expenseId) {
        service.deleteExpense(id, expenseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/photos")
    public ResponseEntity<WorkOrderPhotoDto> addPhoto(@PathVariable String id,
                                                        @RequestBody CreateWorkOrderPhotoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addPhoto(id, request));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable String id, @PathVariable String photoId) {
        service.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }
}
