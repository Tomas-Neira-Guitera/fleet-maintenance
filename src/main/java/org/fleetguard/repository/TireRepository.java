package org.fleetguard.repository;

import org.fleetguard.entity.Tire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TireRepository extends JpaRepository<Tire, UUID> {

    List<Tire> findAllByOrderByBrandAscCreatedAtAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
