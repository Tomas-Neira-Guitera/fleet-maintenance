package org.fleetguard.repository;

import org.fleetguard.entity.TrailerCoupling;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrailerCouplingRepository extends JpaRepository<TrailerCoupling, UUID> {

    Optional<TrailerCoupling> findFirstByTrailer_IdAndUncoupledAtIsNull(UUID trailerId);

    Optional<TrailerCoupling> findFirstByTowing_IdAndUncoupledAtIsNull(UUID towingId);

    List<TrailerCoupling> findByTrailer_IdOrTowing_IdOrderByCoupledAtDesc(UUID trailerId, UUID towingId);
}
