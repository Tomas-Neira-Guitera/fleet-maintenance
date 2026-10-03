package org.fleetguard.repository;

import org.fleetguard.entity.InspectionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InspectionAnswerRepository extends JpaRepository<InspectionAnswer, UUID> {
}
