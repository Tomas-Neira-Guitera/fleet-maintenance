package org.fleetguard.controller;

import org.fleetguard.dto.ExpenseDto;
import org.fleetguard.dto.ListResponse;
import org.fleetguard.service.ExpenseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /expenses, servido en /api/expenses -- gastos de mantenimiento de toda la flota (CAM-52).
 * Solo lectura; exige el JWT de un ADMIN (registrado en WebConfig). Ver CAM-52-expenses-contract.md.
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
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return new ListResponse<>(service.list(vehicleId, category, from, to));
    }
}
