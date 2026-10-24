package org.fleetguard.service;

import org.fleetguard.dto.CreateUserRequest;
import org.fleetguard.dto.UpdateUserRequest;
import org.fleetguard.dto.UserSummaryDto;
import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.exception.UserConflictException;
import org.fleetguard.exception.UserNotFoundException;
import org.fleetguard.exception.UserValidationException;
import org.fleetguard.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(userRepository, passwordEncoder);

    private static User user(String username, Role role) throws Exception {
        User user = new User(username, "hash", role);
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, UUID.randomUUID());
        return user;
    }

    private void saveReturnsTheSameUserWithId() {
        Answer<User> withId = invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                Field field = User.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(saved, UUID.randomUUID());
            }
            return saved;
        };
        when(userRepository.save(any(User.class))).thenAnswer(withId);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(withId);
    }

    @Test
    void listarPorRolDevuelveSoloEsosUsuariosSinElHash() throws Exception {
        when(userRepository.findByRoleOrderByUsernameAsc(Role.TECNICO)).thenReturn(List.of(user("tecnico", Role.TECNICO)));

        List<UserSummaryDto> result = service.list("TECNICO");

        assertEquals(1, result.size());
        assertEquals("tecnico", result.get(0).username());
        assertEquals("TECNICO", result.get(0).role());
        assertTrue(result.get(0).active());
    }

    @Test
    void rolInexistenteDevuelve422() {
        assertThrows(UserValidationException.class, () -> service.list("MECANICO"));
    }

    @Test
    void sinRolListaTodos() throws Exception {
        when(userRepository.findAllByOrderByUsernameAsc())
                .thenReturn(List.of(user("admin", Role.ADMIN), user("chofer", Role.CHOFER)));

        assertEquals(2, service.list(null).size());
        assertEquals(2, service.list(" ").size());
    }

    @Test
    void altaGuardaElHashYNuncaLaContraseñaPlana() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash-de-secreta1");
        saveReturnsTheSameUserWithId();

        UserSummaryDto created = service.create(new CreateUserRequest("  juan.perez ", "secreta1", "TECNICO", null));

        assertEquals("juan.perez", created.username());
        assertEquals("TECNICO", created.role());
        assertTrue(created.active());
        verify(userRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(u -> u.getPasswordHash().equals("hash-de-secreta1")));
    }

    @Test
    void altaQuePierdeLaCarreraContraOtraConElMismoNombreDevuelve409YNo500() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_users\"\n"
                        + "  Detail: Key (username)=(juan) already exists.")));

        UserConflictException ex = assertThrows(UserConflictException.class,
                () -> service.create(new CreateUserRequest("juan", "secreta1", "CHOFER", null)));

        assertEquals("USERNAME_TAKEN", ex.getErrorCode());
    }

    @Test
    void otraViolacionDeIntegridadNoSeDisfrazaDeUsuarioRepetido() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: new row for relation \"users\" violates check constraint \"users_role_check\"")));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.create(new CreateUserRequest("juan", "secreta1", "TECNICO", null)));
    }

    @Test
    void contraseñaDeMenosDe72CaracteresPeroMasDe72BytesDevuelve422() {
        // 40 "ñ" = 40 caracteres, 80 bytes en UTF-8: BCrypt la truncaría en silencio.
        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.create(new CreateUserRequest("juan", "ñ".repeat(40), "CHOFER", null)));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.field().equals("password")));
    }

    @Test
    void altaAceptaEspaciosYAcentosYNormalizaLosEspacios() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash");
        saveReturnsTheSameUserWithId();

        UserSummaryDto created = service.create(new CreateUserRequest("  José   Muñoz ", "secreta1", "TECNICO", null));

        assertEquals("José Muñoz", created.username());
        verify(userRepository).existsByUsernameIgnoreCase("José Muñoz");
    }

    @Test
    void normalizarUnificaLaTildeCombinadaConLaLetraAcentuada() {
        // "e" + U+0301 (tilde combinada) es como algunos teclados mandan la "é".
        assertEquals("José", UserService.normalizeUsername("José"));
        // Espacio duro (NBSP) en el medio y en los extremos, como al pegar desde un documento.
        assertEquals("Juan Pérez", UserService.normalizeUsername(" Juan  Pérez "));
        assertEquals("", UserService.normalizeUsername("   "));
        assertEquals("", UserService.normalizeUsername(null));
    }

    @Test
    void altaConUsuarioRepetidoDevuelve409SinImportarMayusculas() {
        when(userRepository.existsByUsernameIgnoreCase("Admin")).thenReturn(true);

        UserConflictException ex = assertThrows(UserConflictException.class,
                () -> service.create(new CreateUserRequest("Admin", "secreta1", "ADMIN", null)));

        assertEquals("USERNAME_TAKEN", ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void altaConContraseñaDeMenosDeSeisCaracteresDevuelve422() {
        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.create(new CreateUserRequest("juan", "12345", "CHOFER", null)));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.field().equals("password")));
    }

    @Test
    void altaConDatosInvalidosDevuelveTodosLosErroresJuntos() {
        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.create(new CreateUserRequest("juan@perez", null, "MECANICO", null)));

        assertEquals(3, ex.getDetails().size());
    }

    @Test
    void editarCambiaNombreContraseñaYEstado() throws Exception {
        User tecnico = user("tecnico", Role.TECNICO);
        when(userRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(passwordEncoder.encode("nueva123")).thenReturn("hash-nuevo");
        saveReturnsTheSameUserWithId();

        UserSummaryDto updated = service.update(tecnico.getId().toString(),
                new UpdateUserRequest("  Javier   Romero ", "nueva123", false, null, null), UUID.randomUUID());

        // CAM-91: el nombre se guarda en la misma forma canónica que en el alta.
        assertEquals("Javier Romero", updated.username());
        assertEquals("TECNICO", updated.role());
        assertFalse(updated.active());
        assertEquals("hash-nuevo", tecnico.getPasswordHash());
    }

    @Test
    void elEmailEsOpcionalYSeGuardaRecortado() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash");
        saveReturnsTheSameUserWithId();

        UserSummaryDto created = service.create(new CreateUserRequest("juan", "secreta1", "ADMIN", "  juan@flota.com "));

        assertEquals("juan@flota.com", created.email());
    }

    @Test
    void unEmailInvalidoEs422() {
        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.create(new CreateUserRequest("juan", "secreta1", "ADMIN", "juan@flota")));
        assertEquals("email", ex.getDetails().get(0).field());
    }

    @Test
    void editarElEmailYBorrarloConVacio() throws Exception {
        User admin = user("admin", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        saveReturnsTheSameUserWithId();

        service.update(admin.getId().toString(), new UpdateUserRequest(null, null, null, null, "admin@flota.com"), admin.getId());
        assertEquals("admin@flota.com", admin.getEmail());

        service.update(admin.getId().toString(), new UpdateUserRequest(null, null, null, null, null), admin.getId());
        assertEquals("admin@flota.com", admin.getEmail());

        service.update(admin.getId().toString(), new UpdateUserRequest(null, null, null, null, ""), admin.getId());
        assertNull(admin.getEmail());
    }

    @Test
    void elRolNoSePuedeCambiar() throws Exception {
        User tecnico = user("tecnico", Role.TECNICO);
        when(userRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));

        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.update(tecnico.getId().toString(),
                        new UpdateUserRequest(null, null, null, "ADMIN", null), UUID.randomUUID()));

        assertEquals("role", ex.getDetails().get(0).field());
        assertEquals(Role.TECNICO, tecnico.getRole());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void mandarElMismoRolQueYaTieneNoEsUnError() throws Exception {
        User tecnico = user("tecnico", Role.TECNICO);
        when(userRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        saveReturnsTheSameUserWithId();

        UserSummaryDto updated = service.update(tecnico.getId().toString(),
                new UpdateUserRequest(null, null, false, "TECNICO", null), UUID.randomUUID());

        assertEquals("TECNICO", updated.role());
        assertFalse(updated.active());
    }

    @Test
    void renombrarAUnNombreQueYaUsaOtroDevuelve409() throws Exception {
        User chofer = user("chofer1", Role.CHOFER);
        when(userRepository.findById(chofer.getId())).thenReturn(Optional.of(chofer));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("Chofer2", chofer.getId())).thenReturn(true);

        UserConflictException ex = assertThrows(UserConflictException.class,
                () -> service.update(chofer.getId().toString(),
                        new UpdateUserRequest("Chofer2", null, null, null, null), UUID.randomUUID()));

        assertEquals("USERNAME_TAKEN", ex.getErrorCode());
        assertEquals("chofer1", chofer.getUsername());
    }

    @Test
    void unUsuarioPuedeCambiarSoloLasMayusculasDeSuNombre() throws Exception {
        User chofer = user("juan perez", Role.CHOFER);
        when(userRepository.findById(chofer.getId())).thenReturn(Optional.of(chofer));
        saveReturnsTheSameUserWithId();

        // El chequeo de nombre repetido ignora mayúsculas, pero excluye al propio usuario.
        UserSummaryDto updated = service.update(chofer.getId().toString(),
                new UpdateUserRequest("Juan Perez", null, null, null, null), UUID.randomUUID());

        assertEquals("Juan Perez", updated.username());
        verify(userRepository).existsByUsernameIgnoreCaseAndIdNot("Juan Perez", chofer.getId());
    }

    @Test
    void renombrarConUnNombreInvalidoDevuelve422() throws Exception {
        User chofer = user("chofer1", Role.CHOFER);
        when(userRepository.findById(chofer.getId())).thenReturn(Optional.of(chofer));

        UserValidationException vacio = assertThrows(UserValidationException.class,
                () -> service.update(chofer.getId().toString(),
                        new UpdateUserRequest("   ", null, null, null, null), UUID.randomUUID()));
        UserValidationException conArroba = assertThrows(UserValidationException.class,
                () -> service.update(chofer.getId().toString(),
                        new UpdateUserRequest("juan@perez", null, null, null, null), UUID.randomUUID()));

        UserValidationException largo = assertThrows(UserValidationException.class,
                () -> service.update(chofer.getId().toString(),
                        new UpdateUserRequest("x".repeat(31), null, null, null, null), UUID.randomUUID()));

        assertEquals("username", vacio.getDetails().get(0).field());
        assertEquals("username", conArroba.getDetails().get(0).field());
        assertEquals("username", largo.getDetails().get(0).field());
        assertEquals("chofer1", chofer.getUsername());
    }

    @Test
    void renombrarQuePierdeLaCarreraContraOtroConElMismoNombreDevuelve409() throws Exception {
        User chofer = user("chofer1", Role.CHOFER);
        when(userRepository.findById(chofer.getId())).thenReturn(Optional.of(chofer));
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Detail: Key (username)=(juan) already exists.")));

        UserConflictException ex = assertThrows(UserConflictException.class,
                () -> service.update(chofer.getId().toString(),
                        new UpdateUserRequest("juan", null, null, null, null), UUID.randomUUID()));

        assertEquals("USERNAME_TAKEN", ex.getErrorCode());
    }

    @Test
    void otraViolacionDeIntegridadAlEditarNoSeDisfrazaDeNombreRepetido() throws Exception {
        User chofer = user("chofer1", Role.CHOFER);
        when(userRepository.findById(chofer.getId())).thenReturn(Optional.of(chofer));
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("violates check constraint")));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.update(chofer.getId().toString(),
                        new UpdateUserRequest(null, null, false, null, null), UUID.randomUUID()));
    }

    @Test
    void editarConUnaContraseñaCortaDevuelve422() throws Exception {
        User chofer = user("chofer1", Role.CHOFER);
        when(userRepository.findById(chofer.getId())).thenReturn(Optional.of(chofer));

        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.update(chofer.getId().toString(),
                        new UpdateUserRequest(null, "123", null, null, null), UUID.randomUUID()));

        assertEquals("password", ex.getDetails().get(0).field());
    }

    @Test
    void unAdminNoPuedeDesactivarseASiMismo() throws Exception {
        User admin = user("admin", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        UserConflictException ex = assertThrows(UserConflictException.class,
                () -> service.update(admin.getId().toString(), new UpdateUserRequest(null, null, false, null, null), admin.getId()));

        assertEquals("CANNOT_MODIFY_SELF", ex.getErrorCode());
        assertTrue(admin.isActive());
    }

    @Test
    void unAdminSiPuedeCambiarseElNombreYLaContraseña() throws Exception {
        User admin = user("admin", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("otra123")).thenReturn("hash-otra");
        saveReturnsTheSameUserWithId();

        service.update(admin.getId().toString(), new UpdateUserRequest("Administración", "otra123", true, null, null), admin.getId());

        assertEquals("Administración", admin.getUsername());
        assertEquals("hash-otra", admin.getPasswordHash());
    }

    @Test
    void editarUnUsuarioInexistenteDevuelve404() {
        assertThrows(UserNotFoundException.class,
                () -> service.update(UUID.randomUUID().toString(), new UpdateUserRequest(null, null, true, null, null), UUID.randomUUID()));
        assertThrows(UserNotFoundException.class,
                () -> service.update("no-es-un-uuid", new UpdateUserRequest(null, null, true, null, null), UUID.randomUUID()));
    }
}
