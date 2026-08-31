package org.example.controller;

import org.example.dto.DefectDto;
import org.example.service.DefectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /defects, servido en /api/defects -- ver openapi.yaml. */
@RestController
@RequestMapping("/defects")
public class DefectController {

    private final DefectService defectService;

    public DefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    @GetMapping
    public List<DefectDto> listDefects() {
        return defectService.listDefects();
    }
}
