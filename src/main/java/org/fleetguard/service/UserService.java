package org.fleetguard.service;

import org.fleetguard.dto.CreateUserRequest;
import org.fleetguard.dto.FieldValidationErrorDetail;
import org.fleetguard.dto.UpdateUserRequest;
import org.fleetguard.dto.UserSummaryDto;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.exception.UserConflictException;
import org.fleetguard.exception.UserNotFoundException;
import org.fleetguard.exception.UserValidationException;
import org.fleetguard.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gestión de usuarios (CAM-23, CAM-91): listado, alta y edición. Solo la usa un ADMIN --
 * /api/users está protegido por JwtAuthInterceptor. Ver docs/api/CAM-23-users-contract.md.
 */
@Service
public class UserService {

    public static final int USERNAME_MAX_LENGTH = 30;
    public static final int PASSWORD_MIN_LENGTH = 6;
    // Tope de BCrypt, en bytes UTF-8 (no en caracteres): una letra con acento ocupa 2.
    public static final int PASSWORD_MAX_BYTES = 72;
    // Letras (con acentos y ñ), números, espacios, punto, guion y guion bajo: "Juan Pérez" vale.
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[\\p{L}\\p{N} ._-]+");
    // Cualquier tipo de espacio, no solo el ASCII: el espacio duro (NBSP, U+00A0) y parecidos
    // llegan al pegar desde un documento o desde algunos teclados de celular.
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[\\s\\p{Z}]+", Pattern.UNICODE_CHARACTER_CLASS);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Forma canónica de un username (CAM-23), la misma al darlo de alta y al loguearse: sin
     * espacios en los extremos, los espacios repetidos del medio colapsados a uno ("Juan  Pérez"
     * es "Juan Pérez") y en Unicode NFC, para que una "é" escrita como "e" + tilde combinada
     * (como la mandan algunos teclados) sea el mismo usuario que la "é" de un solo carácter.
     * Todo tipo de espacio (también el duro, NBSP) pasa a ser un espacio común.
     */
    public static String normalizeUsername(String raw) {
        if (raw == null) {
            return "";
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        // Primero se unifica todo espacio a ' ' y después se recorta: trim() solo, no saca el NBSP.
        return WHITESPACE_RUN.matcher(nfc).replaceAll(" ").trim();
    }

    /** Sin role lista todos; con role, solo los de ese rol (lo usa el selector de técnico de las OTs). */
    public List<UserSummaryDto> list(String roleParam) {
        List<User> users = roleParam == null || roleParam.isBlank()
                ? userRepository.findAllByOrderByUsernameAsc()
                : userRepository.findByRoleOrderByUsernameAsc(parseRole(roleParam));
        return users.stream().map(UserService::toDto).toList();
    }

    @Transactional
    public UserSummaryDto create(CreateUserRequest request) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        String username = normalizeUsername(request.username());
        validateUsername(username, details);
        validatePassword(request.password(), details);
        Role role = parseRole(request.role(), details);
        if (!details.isEmpty()) {
            throw new UserValidationException("Datos inválidos para crear el usuario", details);
        }

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw usernameTaken(username);
        }
        User user = new User(username, passwordEncoder.encode(request.password()), role);
        try {
            // saveAndFlush para que, si otra alta con el mismo nombre ganó la carrera entre el
            // chequeo de arriba y este insert, el unique de la base se traduzca acá a 409 y no a 500.
            return toDto(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Solo el choque con el unique de username es "ya existe". Cualquier otra violación
            // (p. ej. un CHECK de rol viejo en una base local, ver STATE) sigue como error real.
            if (isUsernameUniqueViolation(e)) {
                throw usernameTaken(username);
            }
            throw e;
        }
    }

    // Postgres describe la violación de un unique como "Key (username)=(...) already exists" (o "Ya
    // existe la llave (username)=..." en castellano). Ese Detail llega en el mensaje porque pgjdbc
    // tiene logServerErrorDetail=true por default: si se apagara en la URL de la base, la carrera
    // entre dos altas con el mismo nombre volvería a dar 500 en vez de 409.
    private static boolean isUsernameUniqueViolation(DataIntegrityViolationException e) {
        String detail = e.getMostSpecificCause().getMessage();
        return detail != null && detail.contains("(username)");
    }

    private static UserConflictException usernameTaken(String username) {
        return new UserConflictException("USERNAME_TAKEN", "Ya existe un usuario \"" + username + "\"");
    }

    /**
     * CAM-91: se edita el nombre, la contraseña y si está activo; el rol queda como se dio de alta.
     * currentUserId: el admin que hace el pedido -- no puede desactivarse a sí mismo.
     */
    @Transactional
    public UserSummaryDto update(String idRaw, UpdateUserRequest request, UUID currentUserId) {
        User user = findUser(idRaw);

        List<FieldValidationErrorDetail> details = new ArrayList<>();
        // Mandar el mismo rol que ya tiene no es un cambio: se acepta sin hacer nada.
        if (request.role() != null && !request.role().equals(user.getRole().name())) {
            details.add(new FieldValidationErrorDetail("role", "El rol de un usuario no se puede cambiar"));
        }
        String newUsername = request.username() == null ? null : normalizeUsername(request.username());
        if (newUsername != null) {
            validateUsername(newUsername, details);
        }
        if (request.password() != null) {
            validatePassword(request.password(), details);
        }
        if (!details.isEmpty()) {
            throw new UserValidationException("Datos inválidos para editar el usuario", details);
        }

        if (user.getId().equals(currentUserId) && Boolean.FALSE.equals(request.active())) {
            throw new UserConflictException("CANNOT_MODIFY_SELF", "No podés desactivar tu propio usuario");
        }
        // Renombrar no rompe nada: inspecciones, viajes y OTs apuntan al usuario por id, y el
        // token también (claim sub), así que la sesión abierta de ese usuario sigue valiendo.
        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(newUsername, user.getId())) {
                throw usernameTaken(newUsername);
            }
            user.setUsername(newUsername);
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        try {
            // Misma carrera que en el alta: dos pedidos que eligen el mismo nombre a la vez.
            return toDto(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            if (isUsernameUniqueViolation(e)) {
                throw usernameTaken(user.getUsername());
            }
            throw e;
        }
    }

    private User findUser(String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            throw new UserNotFoundException(idRaw);
        }
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(idRaw));
    }

    private static void validateUsername(String username, List<FieldValidationErrorDetail> details) {
        if (username.isEmpty()) {
            details.add(new FieldValidationErrorDetail("username", "Obligatorio"));
        } else if (username.length() > USERNAME_MAX_LENGTH) {
            details.add(new FieldValidationErrorDetail("username",
                    "No puede superar los " + USERNAME_MAX_LENGTH + " caracteres"));
        } else if (!USERNAME_PATTERN.matcher(username).matches()) {
            details.add(new FieldValidationErrorDetail("username",
                    "Solo letras, números, espacios, punto, guion y guion bajo"));
        }
    }

    private static void validatePassword(String password, List<FieldValidationErrorDetail> details) {
        if (password == null || password.isBlank()) {
            details.add(new FieldValidationErrorDetail("password", "Obligatoria"));
        } else if (password.length() < PASSWORD_MIN_LENGTH) {
            details.add(new FieldValidationErrorDetail("password",
                    "Tiene que tener al menos " + PASSWORD_MIN_LENGTH + " caracteres"));
        } else if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            // Se mide en bytes porque BCrypt corta ahí: si no, la contraseña se truncaría en silencio.
            details.add(new FieldValidationErrorDetail("password",
                    "Es demasiado larga (máximo " + PASSWORD_MAX_BYTES + " caracteres sin acentos)"));
        }
    }

    private static Role parseRole(String roleParam, List<FieldValidationErrorDetail> details) {
        if (roleParam == null || roleParam.isBlank()) {
            details.add(new FieldValidationErrorDetail("role", "Obligatorio: 'ADMIN', 'CHOFER' o 'TECNICO'"));
            return null;
        }
        try {
            return Role.valueOf(roleParam);
        } catch (IllegalArgumentException e) {
            details.add(new FieldValidationErrorDetail("role", "Debe ser 'ADMIN', 'CHOFER' o 'TECNICO'"));
            return null;
        }
    }

    private static Role parseRole(String roleParam) {
        List<FieldValidationErrorDetail> details = new ArrayList<>();
        Role role = parseRole(roleParam, details);
        if (!details.isEmpty()) {
            throw new UserValidationException("Rol inválido", details);
        }
        return role;
    }

    private static UserSummaryDto toDto(User user) {
        return new UserSummaryDto(user.getId().toString(), user.getUsername(), user.getRole().name(), user.isActive());
    }
}
