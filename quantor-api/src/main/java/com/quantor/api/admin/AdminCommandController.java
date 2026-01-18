package com.quantor.api.admin;

import com.quantor.saas.infrastructure.engine.BotCommandEntity;
import com.quantor.saas.infrastructure.engine.BotCommandRepository;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/commands")
public class AdminCommandController {

  private final BotCommandRepository commands;
  private final BotInstanceRepository instances;

  public AdminCommandController(BotCommandRepository commands, BotInstanceRepository instances) {
    this.commands = commands;
    this.instances = instances;
  }

  public record CommandRow(
      UUID id,
      UUID userId,
      String jobKey,
      String command,
      String status,
      int attempts,
      String workerId,
      String requestId,
      String traceparent,
      String errorMessage,
      String createdAt,
      String processedAt
  ) {}

  @GetMapping
  public List<CommandRow> list(
      @RequestParam(defaultValue = "FAILED") String status,
      @RequestParam(defaultValue = "100") int limit
  ) {
    int size = Math.max(1, Math.min(500, limit));
    var page = commands.findByStatusOrderByCreatedAtDesc(status.toUpperCase(Locale.ROOT),
        PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")));

    // bulk resolve jobKey where possible
    Map<UUID, String> jobKeyByInstanceId = new HashMap<>();
    List<CommandRow> out = new ArrayList<>();
    for (BotCommandEntity c : page) {
      String jobKey = jobKeyByInstanceId.computeIfAbsent(c.getBotInstanceId(), id ->
          instances.findById(id).map(i -> i.getJobKey()).orElse(null)
      );
      out.add(toRow(c, jobKey));
    }
    return out;
  }

  @GetMapping("/{id}")
  public CommandRow get(@PathVariable UUID id) {
    BotCommandEntity c = commands.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Command not found"));
    String jobKey = instances.findById(c.getBotInstanceId()).map(i -> i.getJobKey()).orElse(null);
    return toRow(c, jobKey);
  }

  private static CommandRow toRow(BotCommandEntity c, String jobKey) {
    return new CommandRow(
        c.getId(),
        c.getUserId(),
        jobKey,
        c.getCommand(),
        c.getStatus(),
        c.getAttempts(),
        c.getWorkerId(),
        c.getRequestId(),
        c.getTraceparent(),
        c.getErrorMessage(),
        c.getCreatedAt() == null ? null : c.getCreatedAt().toString(),
        c.getProcessedAt() == null ? null : c.getProcessedAt().toString()
    );
  }
}
