package org.example.controller;

import org.example.auth.AuthenticatedUser;
import org.example.dto.CreateUserRequest;
import org.example.dto.ListResponse;
import org.example.dto.UpdateUserRequest;
import org.example.dto.UserSummaryDto;
import org.example.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /users, servido en /api/users -- gestión de usuarios (CAM-23). Todo el controller exige un
 * JWT de ADMIN (JwtAuthInterceptor, registrado en WebConfig). Ver docs/api/CAM-23-users-contract.md.
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

    @PostMapping
    public ResponseEntity<UserSummaryDto> create(@RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    public UserSummaryDto update(@PathVariable String id, @RequestBody UpdateUserRequest request,
                                 @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser currentUser) {
        return service.update(id, request, currentUser.id());
    }
}
