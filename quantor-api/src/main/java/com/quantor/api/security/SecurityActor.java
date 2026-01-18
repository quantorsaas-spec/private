package com.quantor.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * Provides a stable way for service-layer code to understand who is acting.
 *
 * Normal mode:
 * - effective user = JWT subject
 * - actorType = USER
 * - actorId = JWT subject
 *
 * Support/impersonation mode:
 * - effective user = JWT subject (the customer)
 * - actorType = ADMIN
 * - actorId = claim "actorId" (the admin)
 */
public final class SecurityActor {

  private SecurityActor() {}

  public static ActorInfo current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jat)) {
      return new ActorInfo("SYSTEM", null, null, false);
    }
    Jwt jwt = jat.getToken();

    String subject = jwt.getSubject();
    UUID effectiveUserId = parseUuidOrNull(subject);

    boolean support = Boolean.TRUE.equals(jwt.getClaim("support"));
    String actorIdStr = jwt.getClaimAsString("actorId");
    UUID actorId = parseUuidOrNull(actorIdStr);

    if (support && actorId != null) {
      return new ActorInfo("ADMIN", actorId, effectiveUserId, true);
    }

    // default: user acts as themselves
    return new ActorInfo("USER", effectiveUserId, effectiveUserId, false);
  }

  private static UUID parseUuidOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  public record ActorInfo(String actorType, UUID actorId, UUID effectiveUserId, boolean support) {}
}
