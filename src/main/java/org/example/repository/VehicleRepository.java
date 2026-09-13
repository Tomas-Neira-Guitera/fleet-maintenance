package org.example.repository;

import org.example.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByActive(boolean active);

    boolean existsByPlateIgnoreCase(String plate);

    boolean existsByPlateIgnoreCaseAndIdNot(String plate, UUID id);
}
