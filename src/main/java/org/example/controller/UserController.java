package org.example.controller;

import org.example.dto.ListResponse;
import org.example.dto.UserSummaryDto;
import org.example.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /users, servido en /api/users -- solo lectura (CAM-60), para poblar el selector de
 * técnico de una orden de trabajo. El alta y gestión de usuarios es otra card (CAM-23).
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public ListResponse<UserSummaryDto> list(@RequestParam(required = false) String role) {
        List<UserSummaryDto> users = service.list(role);
        return new ListResponse<>(users);
    }
}
