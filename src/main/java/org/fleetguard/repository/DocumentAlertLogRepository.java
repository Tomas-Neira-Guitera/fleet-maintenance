package org.fleetguard.repository;

import org.fleetguard.entity.DocumentAlertLog;
import org.fleetguard.entity.DocumentAlertStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface DocumentAlertLogRepository extends JpaRepository<DocumentAlertLog, UUID> {

    boolean existsByDocumentIdAndStageAndExpiresAt(UUID documentId, DocumentAlertStage stage, LocalDate expiresAt);
}
