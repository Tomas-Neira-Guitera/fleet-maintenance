package org.example.repository;

import org.example.entity.InspectionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InspectionAnswerRepository extends JpaRepository<InspectionAnswer, UUID> {
}
