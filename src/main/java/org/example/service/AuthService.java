package org.example.service;

import org.example.dto.LoginRequestDto;
import org.example.dto.LoginResponseDto;
import org.example.entity.User;
import org.example.exception.InvalidCredentialsException;
import org.example.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Lógica de negocio de POST /api/auth/login -- ver CAM-43. */
@Service
public class AuthService {

    /**
     * Hash BCrypt de una contraseña que no le pertenece a nadie. Cuando el
     * usuario no existe, igual se corre el matches() contra este hash para
     * que la respuesta tarde lo mismo que un login con contraseña incorrecta
     * -- si no, el tiempo de respuesta delata qué usuarios existen aunque el
     * mensaje de error sea idéntico.
     */
    private static final String DUMMY_HASH = "$2a$10$1oCjvn4RlOU7qKZbR/fGCO.MyANHy4cD06qVB18eWVO7R9gouX5TS";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponseDto login(LoginRequestDto request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new InvalidCredentialsException();
        }
        Optional<User> maybeUser = userRepository.findByUsername(request.username());
        String hashToCheck = maybeUser.map(User::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);
        if (maybeUser.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        User user = maybeUser.get();
        String token = jwtService.generateToken(user);
        return new LoginResponseDto(token, user.getRole().name());
    }
}
