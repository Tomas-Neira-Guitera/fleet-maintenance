package org.fleetguard.controller;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.ListResponse;
import org.fleetguard.dto.VehicleExpenseRequest;
import org.fleetguard.service.ExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /expenses, servido en /api/expenses -- gastos de toda la flota (CAM-52) y alta, edición y baja de
 * los operativos (CAM-109). Exige el JWT de un ADMIN (registrado en WebConfig). Ver
 * CAM-52-expenses-contract.md y CAM-109-operating-expenses-contract.md.
 */
@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public ListResponse<ExpenseDto> list(@RequestParam(required = false) String vehicleId,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String origin,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return new ListResponse<>(service.list(vehicleId, category, origin, from, to));
    }

    @PostMapping
    public ResponseEntity<ExpenseDto> create(@RequestBody VehicleExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ExpenseDto update(@PathVariable String id, @RequestBody VehicleExpenseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
