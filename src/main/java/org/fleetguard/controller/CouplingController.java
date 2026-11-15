package org.fleetguard.controller;

import org.fleetguard.auth.AuthenticatedUser;
import org.fleetguard.dto.CoupleRequest;
import org.fleetguard.dto.CouplingDto;
import org.fleetguard.entity.Role;
import org.fleetguard.exception.ForbiddenException;
import org.fleetguard.service.CouplingService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Enganche de remolques (CAM-157). Leerlo: ADMIN o CHOFER; engancharlo y desengancharlo: ADMIN (WebConfig). */
@RestController
public class CouplingController {

    private final CouplingService service;

    public CouplingController(CouplingService service) {
        this.service = service;
    }

    @GetMapping("/vehicles/{id}/coupling")
    public CouplingDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/vehicles/{id}/coupling")
    public CouplingDto couple(@PathVariable String id, @RequestBody(required = false) CoupleRequest request,
                              @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        requireAdmin(user);
        return service.couple(id, request, user.username());
    }

    @DeleteMapping("/vehicles/{id}/coupling")
    public CouplingDto uncouple(@PathVariable String id,
                                @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser user) {
        requireAdmin(user);
        return service.uncouple(id);
    }

    // La ruta la lee también el chofer (WebConfig); cambiar el enganche es del admin.
    private static void requireAdmin(AuthenticatedUser user) {
        if (user.role() != Role.ADMIN) {
            throw new ForbiddenException();
        }
    }
}
