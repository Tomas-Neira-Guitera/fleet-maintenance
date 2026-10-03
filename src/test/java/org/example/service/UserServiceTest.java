package org.example.service;

import org.example.dto.CreateUserRequest;
import org.example.dto.UpdateUserRequest;
import org.example.dto.UserSummaryDto;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.exception.UserConflictException;
import org.example.exception.UserNotFoundException;
import org.example.exception.UserValidationException;
import org.example.repository.UserRepository;
import org.example.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(userRepository, workOrderRepository, passwordEncoder);

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

        UserSummaryDto created = service.create(new CreateUserRequest("  juan.perez ", "secreta1", "TECNICO"));

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
                () -> service.create(new CreateUserRequest("juan", "secreta1", "CHOFER")));

        assertEquals("USERNAME_TAKEN", ex.getErrorCode());
    }

    @Test
    void otraViolacionDeIntegridadNoSeDisfrazaDeUsuarioRepetido() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: new row for relation \"users\" violates check constraint \"users_role_check\"")));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.create(new CreateUserRequest("juan", "secreta1", "TECNICO")));
    }

    @Test
    void contraseñaDeMenosDe72CaracteresPeroMasDe72BytesDevuelve422() {
        // 40 "ñ" = 40 caracteres, 80 bytes en UTF-8: BCrypt la truncaría en silencio.
        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.create(new CreateUserRequest("juan", "ñ".repeat(40), "CHOFER")));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.field().equals("password")));
    }

    @Test
    void tecnicoConOtsAbiertasNoPuedeCambiarDeRol() throws Exception {
        User tecnico = user("tecnico", Role.TECNICO);
        when(userRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(workOrderRepository.countByTechnicianIdAndStatusIn(eq(tecnico.getId()), any())).thenReturn(2L);

        UserConflictException ex = assertThrows(UserConflictException.class,
                () -> service.update(tecnico.getId().toString(), new UpdateUserRequest("CHOFER", null, null), UUID.randomUUID()));

        assertEquals("TECHNICIAN_HAS_OPEN_WORK_ORDERS", ex.getErrorCode());
        assertEquals(Role.TECNICO, tecnico.getRole());
    }

    @Test
    void tecnicoConOtsAbiertasSiPuedeCambiarLaContraseñaODesactivarse() throws Exception {
        User tecnico = user("tecnico", Role.TECNICO);
        when(userRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(workOrderRepository.countByTechnicianIdAndStatusIn(eq(tecnico.getId()), any())).thenReturn(2L);
        when(passwordEncoder.encode("nueva123")).thenReturn("hash-nuevo");
        saveReturnsTheSameUserWithId();

        UserSummaryDto updated = service.update(tecnico.getId().toString(),
                new UpdateUserRequest("TECNICO", "nueva123", false), UUID.randomUUID());

        assertEquals("TECNICO", updated.role());
        assertFalse(updated.active());
    }

    @Test
    void altaAceptaEspaciosYAcentosYNormalizaLosEspacios() {
        when(passwordEncoder.encode("secreta1")).thenReturn("hash");
        saveReturnsTheSameUserWithId();

        UserSummaryDto created = service.create(new CreateUserRequest("  José   Muñoz ", "secreta1", "TECNICO"));

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
                () -> service.create(new CreateUserRequest("Admin", "secreta1", "ADMIN")));

        assertEquals("USERNAME_TAKEN", ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void altaConContraseñaDeMenosDeSeisCaracteresDevuelve422() {
        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.create(new CreateUserRequest("juan", "12345", "CHOFER")));

        assertTrue(ex.getDetails().stream().anyMatch(d -> d.field().equals("password")));
    }

    @Test
    void altaConDatosInvalidosDevuelveTodosLosErroresJuntos() {
        UserValidationException ex = assertThrows(UserValidationException.class,
                () -> service.create(new CreateUserRequest("juan@perez", null, "MECANICO")));

        assertEquals(3, ex.getDetails().size());
    }

    @Test
    void editarCambiaRolContraseñaYEstado() throws Exception {
        User tecnico = user("tecnico", Role.TECNICO);
        when(userRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(passwordEncoder.encode("nueva123")).thenReturn("hash-nuevo");
        saveReturnsTheSameUserWithId();

        UserSummaryDto updated = service.update(tecnico.getId().toString(),
                new UpdateUserRequest("CHOFER", "nueva123", false), UUID.randomUUID());

        assertEquals("CHOFER", updated.role());
        assertFalse(updated.active());
        assertEquals("hash-nuevo", tecnico.getPasswordHash());
    }

    @Test
    void unAdminNoPuedeDesactivarseASiMismo() throws Exception {
        User admin = user("admin", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        UserConflictException ex = assertThrows(UserConflictException.class,
                () -> service.update(admin.getId().toString(), new UpdateUserRequest(null, null, false), admin.getId()));

        assertEquals("CANNOT_MODIFY_SELF", ex.getErrorCode());
        assertTrue(admin.isActive());
    }

    @Test
    void unAdminNoPuedeQuitarseElRolAdmin() throws Exception {
        User admin = user("admin", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(UserConflictException.class,
                () -> service.update(admin.getId().toString(), new UpdateUserRequest("TECNICO", null, null), admin.getId()));
        assertEquals(Role.ADMIN, admin.getRole());
    }

    @Test
    void unAdminSiPuedeCambiarseLaContraseña() throws Exception {
        User admin = user("admin", Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("otra123")).thenReturn("hash-otra");
        saveReturnsTheSameUserWithId();

        service.update(admin.getId().toString(), new UpdateUserRequest("ADMIN", "otra123", true), admin.getId());

        assertEquals("hash-otra", admin.getPasswordHash());
    }

    @Test
    void editarUnUsuarioInexistenteDevuelve404() {
        assertThrows(UserNotFoundException.class,
                () -> service.update(UUID.randomUUID().toString(), new UpdateUserRequest(null, null, true), UUID.randomUUID()));
        assertThrows(UserNotFoundException.class,
                () -> service.update("no-es-un-uuid", new UpdateUserRequest(null, null, true), UUID.randomUUID()));
    }
}
