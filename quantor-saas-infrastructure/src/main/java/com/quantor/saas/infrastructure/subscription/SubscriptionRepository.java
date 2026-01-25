package com.quantor.saas.infrastructure.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
  Optional<SubscriptionEntity> findByExternalSubscriptionId(String externalSubscriptionId);
  Optional<SubscriptionEntity> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);
}
