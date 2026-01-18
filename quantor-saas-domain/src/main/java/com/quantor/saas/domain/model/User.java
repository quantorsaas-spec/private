package com.quantor.saas.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal SaaS user aggregate (no infrastructure concerns).
 */
public record User(
        UUID id,
        String email,
        String passwordHash,
        Role role,
        Instant createdAt
) {
    public enum Role {
        USER,
        ADMIN
    }
}
