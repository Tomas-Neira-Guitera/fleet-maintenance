package org.fleetguard.controller;

import org.fleetguard.dto.ApplyChecklistTemplateRequest;
import org.fleetguard.dto.ApplyChecklistTemplateResultDto;
import org.fleetguard.dto.ChecklistTemplateDto;
import org.fleetguard.dto.ChecklistTemplateRequest;
import org.fleetguard.dto.ListResponse;
import org.fleetguard.service.ChecklistTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Plantillas de checklist (CAM-150). Solo ADMIN, registrado en WebConfig. */
@RestController
@RequestMapping("/checklist-templates")
public class ChecklistTemplateController {

    private final ChecklistTemplateService service;

    public ChecklistTemplateController(ChecklistTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ListResponse<ChecklistTemplateDto> list() {
        return new ListResponse<>(service.list());
    }

    @GetMapping("/{id}")
    public ChecklistTemplateDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ChecklistTemplateDto> create(@RequestBody ChecklistTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ChecklistTemplateDto update(@PathVariable String id, @RequestBody ChecklistTemplateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/apply")
    public ApplyChecklistTemplateResultDto apply(@PathVariable String id,
                                                 @RequestBody ApplyChecklistTemplateRequest request) {
        return service.apply(id, request);
    }
}
