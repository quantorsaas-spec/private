package com.quantor.api.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// NOTE: There is also com.quantor.api.me.MeController.
// Default Spring bean name would be "meController" for both, causing a startup failure.
// Give this controller an explicit bean name to avoid collisions.
@RestController("authMeController")
@RequestMapping("/api/v1/auth")
public class MeController {

  /**
   * Returns the authenticated user's identifiers.
   *
   * The access token uses:
   *  - sub   = internal user UUID
   *  - email = user's email
   *  - role  = USER/ADMIN
   */
  @GetMapping("/me")
  public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", jwt.getSubject());
    out.put("email", jwt.getClaimAsString("email"));
    out.put("role", jwt.getClaimAsString("role"));
    return out;
  }
}
