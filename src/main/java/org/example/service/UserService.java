package org.example.service;

import org.example.dto.FieldValidationErrorDetail;
import org.example.dto.UserSummaryDto;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.exception.UserValidationException;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Listado de usuarios de solo lectura (CAM-60) -- hoy lo usa el selector de técnico de las OTs. */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** role es obligatorio: sin JWT que proteja el endpoint, no se expone el listado completo de usuarios. */
    public List<UserSummaryDto> list(String roleParam) {
        List<User> users = userRepository.findByRoleOrderByUsernameAsc(parseRole(roleParam));
        return users.stream()
                .map(u -> new UserSummaryDto(u.getId().toString(), u.getUsername(), u.getRole().name()))
                .toList();
    }

    private Role parseRole(String roleParam) {
        if (roleParam == null || roleParam.isBlank()) {
            throw new UserValidationException("Falta el rol",
                    List.of(new FieldValidationErrorDetail("role", "Obligatorio: 'ADMIN', 'CHOFER' o 'TECNICO'")));
        }
        try {
            return Role.valueOf(roleParam);
        } catch (IllegalArgumentException e) {
            throw new UserValidationException("Rol inválido",
                    List.of(new FieldValidationErrorDetail("role", "Debe ser 'ADMIN', 'CHOFER' o 'TECNICO'")));
        }
    }
}
