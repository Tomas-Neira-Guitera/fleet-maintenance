package org.fleetguard.service;

import org.fleetguard.entity.User;
import org.fleetguard.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nombre actual de un usuario a partir de su id (CAM-85). Inspecciones y viajes guardan el nombre
 * del momento solo como respaldo: si al usuario lo renombran (CAM-91), se ve el nombre nuevo.
 */
@Service
public class UserNameService {

    private final UserRepository userRepository;

    public UserNameService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Clave: el id como texto. Los ids que no son de un usuario (datos anteriores a CAM-85) no aparecen. */
    public Map<String, String> currentNames(Collection<String> userIds) {
        List<UUID> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(Uuids::parse)
                .flatMap(Optional::stream)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(user -> user.getId().toString(), User::getUsername));
    }
}
