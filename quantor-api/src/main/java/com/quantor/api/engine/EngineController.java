package com.quantor.api.engine;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine")
public class EngineController {

  private final EngineInstanceService engine;

  public EngineController(EngineInstanceService engine) {
    this.engine = engine;
  }

  @PostMapping("/start")
  public ResponseEntity<Map<String, Object>> start(@AuthenticationPrincipal Jwt jwt, @RequestBody StartEngineRequest req) {
    String userId = jwt == null ? null : jwt.getSubject();
    // if request contains userId, it must match JWT (prevents spoofing)
    if (req.userId() != null && userId != null && !req.userId().trim().equals(userId.trim())) {
      throw new IllegalArgumentException("Forbidden: userId mismatch");
    }

    var res = engine.start(userId, req);
    return ResponseEntity.ok(Map.of(
      "status", "ok",
      "action", res.action(),
      "jobKey", res.jobKey(),
      "periodMs", res.periodMs(),
      "ts", Instant.now().toString()
    ));
  }

  @PostMapping("/stop")
  public ResponseEntity<Map<String, Object>> stop(@AuthenticationPrincipal Jwt jwt, @RequestBody EngineJobRequest req) {
    String userId = jwt == null ? null : jwt.getSubject();
    if (req.userId() != null && userId != null && !req.userId().trim().equals(userId.trim())) {
      throw new IllegalArgumentException("Forbidden: userId mismatch");
    }

    var res = engine.stop(userId, req);
    return ResponseEntity.ok(Map.of(
      "status", "ok",
      "action", res.action(),
      "jobKey", res.jobKey(),
      "ts", Instant.now().toString()
    ));
  }

  @PostMapping("/pause")
  public ResponseEntity<Map<String, Object>> pause(@AuthenticationPrincipal Jwt jwt, @RequestBody EngineJobRequest req) {
    String userId = jwt == null ? null : jwt.getSubject();
    if (req.userId() != null && userId != null && !req.userId().trim().equals(userId.trim())) {
      throw new IllegalArgumentException("Forbidden: userId mismatch");
    }

    var res = engine.pause(userId, req);
    return ResponseEntity.ok(Map.of(
      "status", "ok",
      "action", res.action(),
      "jobKey", res.jobKey(),
      "ts", Instant.now().toString()
    ));
  }

  @PostMapping("/resume")
  public ResponseEntity<Map<String, Object>> resume(@AuthenticationPrincipal Jwt jwt, @RequestBody EngineJobRequest req) {
    String userId = jwt == null ? null : jwt.getSubject();
    if (req.userId() != null && userId != null && !req.userId().trim().equals(userId.trim())) {
      throw new IllegalArgumentException("Forbidden: userId mismatch");
    }

    var res = engine.resume(userId, req);
    return ResponseEntity.ok(Map.of(
      "status", "ok",
      "action", res.action(),
      "jobKey", res.jobKey(),
      "ts", Instant.now().toString()
    ));
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> status() {
    // status text from core sessions is kept in SessionService; this endpoint remains as-is for now
    return ResponseEntity.ok(Map.of(
      "status", "ok",
      "text", engine.statusText(),
      "ts", Instant.now().toString()
    ));
  }
}
