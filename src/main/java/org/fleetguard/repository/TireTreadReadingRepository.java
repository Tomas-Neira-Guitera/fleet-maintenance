package org.fleetguard.repository;

import org.fleetguard.entity.TireTreadReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TireTreadReadingRepository extends JpaRepository<TireTreadReading, UUID> {

    List<TireTreadReading> findAllByOrderByMeasuredAtDesc();

    List<TireTreadReading> findByTire_IdOrderByMeasuredAtDesc(UUID tireId);
}
