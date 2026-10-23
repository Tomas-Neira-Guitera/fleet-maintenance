package org.fleetguard.repository;

import org.fleetguard.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserId(UUID userId);

    List<PushSubscription> findByUserIdIn(List<UUID> userIds);

    Optional<PushSubscription> findByEndpoint(String endpoint);
}
