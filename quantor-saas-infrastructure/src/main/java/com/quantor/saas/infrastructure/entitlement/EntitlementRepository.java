package com.quantor.saas.infrastructure.entitlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EntitlementRepository extends JpaRepository<EntitlementEntity, UUID> {
}
