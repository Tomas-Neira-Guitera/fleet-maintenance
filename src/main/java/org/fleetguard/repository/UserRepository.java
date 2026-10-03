package org.fleetguard.repository;

import org.fleetguard.entity.Role;
import org.fleetguard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    List<User> findByRoleOrderByUsernameAsc(Role role);

    List<User> findAllByOrderByUsernameAsc();

    boolean existsByUsernameIgnoreCase(String username);
}
