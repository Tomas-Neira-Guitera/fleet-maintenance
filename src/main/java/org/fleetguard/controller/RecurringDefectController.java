package org.fleetguard.controller;

import org.fleetguard.dto.RecurringDefectsDto;
import org.fleetguard.service.RecurringDefectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /defects/recurring, servido en /api/defects/recurring -- controles que fallan seguido (CAM-135).
 * Aparte de DefectController porque, a diferencia de /defects, exige el JWT de un ADMIN (registrado
 * en WebConfig). Ver CAM-135-recurring-defects-contract.md.
 */
@RestController
@RequestMapping("/defects/recurring")
public class RecurringDefectController {

    private final RecurringDefectService service;

    public RecurringDefectController(RecurringDefectService service) {
        this.service = service;
    }

    @GetMapping
    public RecurringDefectsDto list(@RequestParam(required = false) String vehicleId) {
        return service.list(vehicleId);
    }
}
