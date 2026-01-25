package com.quantor.api.me;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Explicit bean name to avoid accidental clashes with other *MeController classes
@RestController("userMeController")
public class MeController {

  @GetMapping("/api/v1/me")
  public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "userId", jwt.getSubject(),
        "email", jwt.getClaimAsString("email"),
        "role", jwt.getClaimAsString("role")
    );
  }
}
