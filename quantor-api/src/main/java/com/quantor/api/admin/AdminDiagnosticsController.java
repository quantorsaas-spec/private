package com.quantor.api.admin;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/diagnostics")
public class AdminDiagnosticsController {

  private final AdminDiagnosticsService diagnostics;

  public AdminDiagnosticsController(AdminDiagnosticsService diagnostics) {
    this.diagnostics = diagnostics;
  }

  /**
   * One-shot support endpoint: explains why user can/can't start a bot right now.
   */
  @GetMapping("/start-eligibility/{userId}")
  public AdminDiagnosticsService.StartEligibility startEligibility(@PathVariable UUID userId) {
    return diagnostics.diagnoseStartEligibility(userId);
  }

  /**
   * Support endpoint: explains why a bot instance is in ERROR (last FAILED command + trace context).
   */
  @GetMapping("/bot-error/{jobKey}")
  public AdminDiagnosticsService.BotError botError(@PathVariable String jobKey) {
    return diagnostics.diagnoseBotError(jobKey);
  }
}
