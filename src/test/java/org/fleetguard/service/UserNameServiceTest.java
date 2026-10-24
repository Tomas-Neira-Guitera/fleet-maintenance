package org.fleetguard.service;

import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.fleetguard.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** CAM-85: el nombre que se muestra de un chofer es el actual del usuario, no el guardado en la inspección. */
class UserNameServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserNameService service = new UserNameService(userRepository);

    private User userInDb(String username) throws Exception {
        User user = new User(username, "hash", Role.CHOFER);
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, UUID.randomUUID());
        when(userRepository.findAllById(List.of(user.getId()))).thenReturn(List.of(user));
        return user;
    }

    @Test
    void devuelveElNombreActualDeLosIdsQueSonUsuarios() throws Exception {
        User chofer = userInDb("Juan Pérez");
        String id = chofer.getId().toString();

        // Repetidos, nulos e ids viejos que no son un UUID ("driver-demo-1") se ignoran sin romper.
        Map<String, String> names = service.currentNames(Arrays.asList(id, id, null, "driver-demo-1"));

        assertEquals(Map.of(id, "Juan Pérez"), names);
    }

    @Test
    void sinNingunIdDeUsuarioNoConsultaLaBase() {
        assertTrue(service.currentNames(Arrays.asList("driver-demo-1", null)).isEmpty());
        verifyNoInteractions(userRepository);
    }
}
