package com.quantor.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
      @Size(min = 8, max = 72) String password
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

  public record AuthResponse(
      String accessToken,
      String tokenType,
      long expiresInSeconds,
      String refreshTokenId,
      String refreshTokenSecret,
      long refreshExpiresInSeconds
  ) {}

  @PostMapping("/register")
  public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
    var t = auth.register(req.email(), req.password());
    return toResponse(t);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest req) {
    var t = auth.login(req.email(), req.password());
    return toResponse(t);
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
    var t = auth.refresh(UUID.fromString(req.refreshTokenId()), req.refreshTokenSecret());
    return toResponse(t);
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
        t.refreshTokenId().toString(),
        t.refreshTokenSecret(),
        t.refreshExpiresInSeconds()
    );
  }
}
