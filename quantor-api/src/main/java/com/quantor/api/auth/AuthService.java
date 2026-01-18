package com.quantor.api.auth;

import com.quantor.saas.infrastructure.token.RefreshTokenEntity;
import com.quantor.saas.infrastructure.token.RefreshTokenRepository;
import com.quantor.saas.infrastructure.user.UserEntity;
import com.quantor.saas.infrastructure.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;

  private final String issuer;
  private final long accessTokenMinutes;
  private final long refreshTokenDays;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      @Value("${quantor.auth.issuer}") String issuer,
      @Value("${quantor.auth.accessTokenMinutes}") long accessTokenMinutes,
      @Value("${quantor.auth.refreshTokenDays}") long refreshTokenDays
  ) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.issuer = issuer;
    this.accessTokenMinutes = accessTokenMinutes;
    this.refreshTokenDays = refreshTokenDays;
  }

  public AuthTokens register(String email, String password) {
    String normalized = normalizeEmail(email);
    if (users.existsByEmailIgnoreCase(normalized)) {
      throw new IllegalArgumentException("Email already registered");
    }
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();

    var entity = new UserEntity(
        userId,
        normalized,
        passwordEncoder.encode(password),
        "USER",
        true,
        now
    );
    users.save(entity);
    return issueTokens(entity);
  }

  public AuthTokens login(String email, String password) {
    String normalized = normalizeEmail(email);
    var entity = users.findByEmailIgnoreCase(normalized)
        .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

    if (!entity.isEnabled()) {
      throw new IllegalArgumentException("User disabled");
    }
    if (!passwordEncoder.matches(password, entity.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid email or password");
    }
    return issueTokens(entity);
  }

  public AuthTokens refresh(UUID refreshTokenId, String refreshTokenSecret) {
    var token = refreshTokens.findByIdAndRevokedFalse(refreshTokenId)
        .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

    if (token.getExpiresAt().isBefore(Instant.now())) {
      token.setRevoked(true);
      refreshTokens.save(token);
      throw new IllegalArgumentException("Refresh token expired");
    }

    String providedHash = sha256Hex(refreshTokenSecret);
    if (!constantTimeEquals(token.getTokenHash(), providedHash)) {
      throw new IllegalArgumentException("Invalid refresh token");
    }

    var user = users.findById(token.getUserId())
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // rotate refresh token
    token.setRevoked(true);
    refreshTokens.save(token);

    return issueTokens(user);
  }

  public void logout(UUID refreshTokenId) {
    refreshTokens.findById(refreshTokenId).ifPresent(t -> {
      t.setRevoked(true);
      refreshTokens.save(t);
    });
  }

  private AuthTokens issueTokens(UserEntity user) {
    Instant now = Instant.now();
    Instant accessExp = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);

    var claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .issuedAt(now)
        .expiresAt(accessExp)
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole())
        .build();

    String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    long accessTtl = ChronoUnit.SECONDS.between(now, accessExp);

    UUID refreshId = UUID.randomUUID();
    String refreshSecret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    Instant refreshExp = now.plus(refreshTokenDays, ChronoUnit.DAYS);

    refreshTokens.save(new RefreshTokenEntity(
        refreshId,
        user.getId(),
        sha256Hex(refreshSecret),
        false,
        now,
        refreshExp
    ));

    return new AuthTokens(
        accessToken,
        accessTtl,
        refreshId,
        refreshSecret,
        ChronoUnit.SECONDS.between(now, refreshExp)
    );
  }

  private static String normalizeEmail(String email) {
    if (email == null) return "";
    return email.trim().toLowerCase();
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }

  public record AuthTokens(
      String accessToken,
      long accessExpiresInSeconds,
      UUID refreshTokenId,
      String refreshTokenSecret,
      long refreshExpiresInSeconds
  ) {}
}
