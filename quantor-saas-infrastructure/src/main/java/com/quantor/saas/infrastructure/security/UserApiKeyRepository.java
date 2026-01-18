package com.quantor.saas.infrastructure.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserApiKeyRepository extends JpaRepository<UserApiKeyEntity, UUID> {
  boolean existsByUserId(UUID userId);

  boolean existsByUserIdAndExchange(UUID userId, String exchange);
}
