// path: /home/quantor/projects/quantor/Quantor/quantor-saas-infrastructure/src/main/java/com/quantor/saas/infrastructure/engine/BotInstanceRepository.java
package com.quantor.saas.infrastructure.engine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BotInstanceRepository
    extends JpaRepository<BotInstanceEntity, UUID>, BotInstanceRepositoryCustom {

  Optional<BotInstanceEntity> findByJobKey(String jobKey);

  Optional<BotInstanceEntity> findFirstByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, String status);

  long countByUserIdAndStatusIn(UUID userId, Collection<String> statuses);
}
