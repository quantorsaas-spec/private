package com.quantor.api.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine")
public class EngineController {

  private static final Logger log = LoggerFactory.getLogger(EngineController.class);

  private final EngineInstanceService engine;

  public EngineController(EngineInstanceService engine) {
    this.engine = engine;
  }

  @PostMapping("/start")
  public ResponseEntity<Map<String, Object>> start(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody StartEngineRequest req,
      @RequestHeader(value = "X-Caller", required = false) String caller,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
      HttpServletRequest http
  ) {
    String userId = jwt == null ? null : jwt.getSubject();

    // if request contains userId, it must match JWT (prevents spoofing)
    if (req.userId() != null && userId != null && !req.userId().trim().equals(userId.trim())) {
      throw new IllegalArgumentException("Forbidden: userId mismatch");
    }

    var res = engine.start(userId, req);

    // requestId is already in MDC by your filter/interceptor; keep it as-is.
    log.warn("[ENGINE_HTTP] action=START userId={} jobKey={} ip={} xff={} ua={} caller={}",
        userId,
        res.jobKey(),
        http.getRemoteAddr(),
        safe(xff),
        safe(http.getHeader("User-Agent")),
        safe(caller)
    );

    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "action", res.action(),
        "jobKey", res.jobKey(),
        "periodMs", res.periodMs(),
        "ts", Instant.now().toString()
    ));
  }

  @PostMapping("/stop")
  public ResponseEntity<Map<String, Object>> stop(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody EngineJobKeyRequest req,
      @RequestHeader(value = "X-Caller", required = false) String caller,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
      HttpServletRequest http
  ) {
    String userId = jwt == null ? null : jwt.getSubject();
    String jobKey = req == null ? null : req.jobKey();

    var res = engine.stopByJobKey(userId, jobKey);

    log.warn("[ENGINE_HTTP] action=STOP userId={} jobKey={} ip={} xff={} ua={} caller={}",
        userId,
        jobKey,
        http.getRemoteAddr(),
        safe(xff),
        safe(http.getHeader("User-Agent")),
        safe(caller)
    );

    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "action", res.action(),
        "jobKey", res.jobKey(),
        "ts", Instant.now().toString()
    ));
  }

  @PostMapping("/pause")
  public ResponseEntity<Map<String, Object>> pause(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody EngineJobKeyRequest req,
      @RequestHeader(value = "X-Caller", required = false) String caller,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
      HttpServletRequest http
  ) {
    String userId = jwt == null ? null : jwt.getSubject();
    String jobKey = req == null ? null : req.jobKey();

    var res = engine.pauseByJobKey(userId, jobKey);

    log.warn("[ENGINE_HTTP] action=PAUSE userId={} jobKey={} ip={} xff={} ua={} caller={}",
        userId, jobKey, http.getRemoteAddr(), safe(xff), safe(http.getHeader("User-Agent")), safe(caller)
    );

    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "action", res.action(),
        "jobKey", res.jobKey(),
        "ts", Instant.now().toString()
    ));
  }

  @PostMapping("/resume")
  public ResponseEntity<Map<String, Object>> resume(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody EngineJobKeyRequest req,
      @RequestHeader(value = "X-Caller", required = false) String caller,
      @RequestHeader(value = "X-Forwarded-For", required = false) String xff,
      HttpServletRequest http
  ) {
    String userId = jwt == null ? null : jwt.getSubject();
    String jobKey = req == null ? null : req.jobKey();

    var res = engine.resumeByJobKey(userId, jobKey);

    log.warn("[ENGINE_HTTP] action=RESUME userId={} jobKey={} ip={} xff={} ua={} caller={}",
        userId, jobKey, http.getRemoteAddr(), safe(xff), safe(http.getHeader("User-Agent")), safe(caller)
    );

    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "action", res.action(),
        "jobKey", res.jobKey(),
        "ts", Instant.now().toString()
    ));
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> status() {
    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "text", engine.statusText(),
        "ts", Instant.now().toString()
    ));
  }

  private static String safe(String s) {
    if (s == null) return "";
    String t = s.trim();
    return t.length() > 500 ? t.substring(0, 500) : t;
  }
}
