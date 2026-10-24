package org.fleetguard.repository;

import org.fleetguard.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientIdAndDismissedAtIsNullOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNullAndDismissedAtIsNull(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
