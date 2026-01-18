package com.quantor.api.admin;

import com.quantor.api.auth.SupportTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * ADMIN-only support endpoints.
 *
 * This issues a short-lived access token that acts as the customer (JWT subject),
 * while recording the admin as the actor via JWT claims.
 */
@RestController
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {

  private final SupportTokenService supportTokens;
  private final AdminAuditService audit;

  public AdminSupportController(SupportTokenService supportTokens, AdminAuditService audit) {
    this.supportTokens = supportTokens;
    this.audit = audit;
  }

  @PostMapping("/impersonate/{userId}")
  public ResponseEntity<Map<String, Object>> impersonate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("userId") UUID userId
  ) {
    if (jwt == null) throw new IllegalArgumentException("Missing auth");

    UUID adminId = UUID.fromString(jwt.getSubject());
    String adminEmail = jwt.getClaimAsString("email");

    var token = supportTokens.issue(adminId, adminEmail, userId);
    audit.logAdmin(adminId, "SUPPORT_IMPERSONATE", "USER", userId.toString());

    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "accessToken", token.accessToken(),
        "tokenType", "Bearer",
        "expiresInSeconds", token.expiresInSeconds(),
        "userId", token.userId().toString(),
        "userEmail", token.userEmail(),
        "ts", Instant.now().toString()
    ));
  }
}
