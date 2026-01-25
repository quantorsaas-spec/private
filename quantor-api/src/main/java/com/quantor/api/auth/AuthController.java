package com.quantor.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public record RegisterRequest(
      @Email @NotBlank String email,
      @NotBlank @Size(min = 8, max = 72) String password
  ) {}

  public record LoginRequest(
      @Email @NotBlank String email,
      @NotBlank String password
  ) {}

  public record RefreshRequest(
      @NotBlank String refreshTokenId,
      @NotBlank String refreshTokenSecret
  ) {}

  public record LogoutRequest(
      @NotBlank String refreshTokenId
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AuthResponse(
      String accessToken,
      String tokenType,
      long expiresInSeconds,
      String refreshTokenId,
      String refreshTokenSecret,
      long refreshExpiresInSeconds
  ) {}

  @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    var t = auth.register(req.email(), req.password());
    return ResponseEntity.ok(toResponse(t));
  }

  @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
    var t = auth.login(req.email(), req.password());
    return ResponseEntity.ok(toResponse(t));
  }

  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    var t = auth.refresh(UUID.fromString(req.refreshTokenId()), req.refreshTokenSecret());
    return ResponseEntity.ok(toResponse(t));
  }

  @PostMapping("/logout")
  public void logout(@Valid @RequestBody LogoutRequest req) {
    auth.logout(UUID.fromString(req.refreshTokenId()));
  }

  private static AuthResponse toResponse(AuthService.AuthTokens t) {
    return new AuthResponse(
        t.accessToken(),
        "Bearer",
        t.accessExpiresInSeconds(),
        t.refreshTokenId() == null ? null : t.refreshTokenId().toString(),
        t.refreshTokenSecret(),
        t.refreshExpiresInSeconds()
    );
  }
}
