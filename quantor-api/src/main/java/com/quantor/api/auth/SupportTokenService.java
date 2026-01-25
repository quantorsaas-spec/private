package com.quantor.api.auth;

import com.quantor.saas.infrastructure.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Issues short-lived "support/impersonation" access tokens.
 *
 * The token's subject is the target user (customer), but it includes actor claims:
 * - support=true
 * - actorId=<admin id>
 * - actorEmail=<admin email>
 *
 * Any actions performed with such token can be audited as ADMIN acting on behalf of USER.
 */
@Service
public class SupportTokenService {

  private final UserRepository users;
  private final JwtEncoder jwtEncoder;
  private final String issuer;
  private final long supportTokenMinutes;

  public SupportTokenService(
      UserRepository users,
      JwtEncoder jwtEncoder,
      @Value("${quantor.auth.issuer}") String issuer,
      @Value("${quantor.auth.supportTokenMinutes}") long supportTokenMinutes
  ) {
    this.users = users;
    this.jwtEncoder = jwtEncoder;
    this.issuer = issuer;
    this.supportTokenMinutes = supportTokenMinutes;
  }

  public SupportToken issue(UUID adminId, String adminEmail, UUID targetUserId) {
    var user = users.findById(targetUserId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (!user.isEnabled()) {
      throw new IllegalArgumentException("User disabled");
    }

    Instant now = Instant.now();
    Instant exp = now.plus(supportTokenMinutes, ChronoUnit.MINUTES);

    var claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .issuedAt(now)
        .expiresAt(exp)
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole())
        // support/impersonation
        .claim("support", true)
        .claim("actorId", adminId.toString())
        .claim("actorEmail", adminEmail)
        .build();

    String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    long ttl = ChronoUnit.SECONDS.between(now, exp);

    return new SupportToken(token, ttl, user.getId(), user.getEmail());
  }

  public record SupportToken(String accessToken, long expiresInSeconds, UUID userId, String userEmail) {}
}
