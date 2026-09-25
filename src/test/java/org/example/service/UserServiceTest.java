package org.example.service;

import org.example.dto.UserSummaryDto;
import org.example.entity.Role;
import org.example.entity.User;
import org.example.exception.UserValidationException;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService service = new UserService(userRepository);

    private static User user(String username, Role role) throws Exception {
        User user = new User(username, "hash", role);
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, UUID.randomUUID());
        return user;
    }

    @Test
    void listarPorRolDevuelveSoloEsosUsuariosSinElHash() throws Exception {
        when(userRepository.findByRoleOrderByUsernameAsc(Role.TECNICO)).thenReturn(List.of(user("tecnico", Role.TECNICO)));

        List<UserSummaryDto> result = service.list("TECNICO");

        assertEquals(1, result.size());
        assertEquals("tecnico", result.get(0).username());
        assertEquals("TECNICO", result.get(0).role());
    }

    @Test
    void rolInexistenteDevuelve422() {
        assertThrows(UserValidationException.class, () -> service.list("MECANICO"));
    }

    @Test
    void sinRolDevuelve422EnVezDeListarTodos() {
        assertThrows(UserValidationException.class, () -> service.list(null));
        assertThrows(UserValidationException.class, () -> service.list(" "));
    }
}
