package org.example.service;

import org.example.dto.LoginRequestDto;
import org.example.dto.LoginResponseDto;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.exception.InvalidCredentialsException;
import org.example.exception.UserInactiveException;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);

    @Test
    void credencialesValidasDevuelveTokenYRol() {
        User admin = new User("admin", "hash-de-admin123", Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin123", "hash-de-admin123")).thenReturn(true);
        when(jwtService.generateToken(admin)).thenReturn("token-firmado");

        LoginResponseDto result = service.login(new LoginRequestDto("admin", "admin123"));

        assertEquals("token-firmado", result.token());
        assertEquals("ADMIN", result.role());
    }

    @Test
    void contraseñaIncorrectaLanzaInvalidCredentials() {
        User admin = new User("admin", "hash-de-admin123", Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("mal", "hash-de-admin123")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDto("admin", "mal")));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void usuarioInexistenteLanzaInvalidCredentials() {
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDto("noexiste", "x")));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void usuarioInexistenteIgualCorreElHasheoParaNoFiltrarPorTiming() {
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("x"), any())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDto("noexiste", "x")));

        // Mismo costo que una contraseña incorrecta sobre un usuario real: sin este
        // llamado, la respuesta para "usuario inexistente" sería mensurablemente más
        // rápida que la de "contraseña incorrecta" y delataría qué usuarios existen.
        verify(passwordEncoder).matches(eq("x"), any());
    }

    @Test
    void elLoginBuscaElUsuarioNormalizandoEspacios() {
        User tecnico = new User("José Muñoz", "hash", Role.TECNICO);
        when(userRepository.findByUsername("José Muñoz")).thenReturn(Optional.of(tecnico));
        when(passwordEncoder.matches("secreta1", "hash")).thenReturn(true);
        when(jwtService.generateToken(tecnico)).thenReturn("token");

        LoginResponseDto result = service.login(new LoginRequestDto("  José   Muñoz ", "secreta1"));

        assertEquals("TECNICO", result.role());
    }

    @Test
    void usuarioDesactivadoConContraseñaCorrectaLanzaUserInactive() {
        User tecnico = new User("tecnico", "hash", Role.TECNICO);
        tecnico.setActive(false);
        when(userRepository.findByUsername("tecnico")).thenReturn(Optional.of(tecnico));
        when(passwordEncoder.matches("tecnico123", "hash")).thenReturn(true);

        assertThrows(UserInactiveException.class,
                () -> service.login(new LoginRequestDto("tecnico", "tecnico123")));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void usuarioDesactivadoConContraseñaIncorrectaNoDelataQueEstaDesactivado() {
        User tecnico = new User("tecnico", "hash", Role.TECNICO);
        tecnico.setActive(false);
        when(userRepository.findByUsername("tecnico")).thenReturn(Optional.of(tecnico));
        when(passwordEncoder.matches("mal", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDto("tecnico", "mal")));
    }

    @Test
    void credencialesVaciasLanzaInvalidCredentialsSinConsultarLaBase() {
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDto("", "")));
        verifyNoInteractions(userRepository, passwordEncoder, jwtService);
    }
}
