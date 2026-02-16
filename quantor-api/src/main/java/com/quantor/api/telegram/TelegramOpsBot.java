// File: quantor-api/src/main/java/com/quantor/api/telegram/TelegramOpsBot.java
package com.quantor.api.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.saas.infrastructure.engine.BotInstanceEntity;
import com.quantor.saas.infrastructure.engine.BotInstanceRepository;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Minimal Telegram "ops" bot for local/dev MVP.
 *
 * IMPORTANT:
 * - Must NOT run together with another long-polling bot on the SAME token (HTTP 409).
 * - Enable explicitly with quantor.telegram.ops.enabled=true
 *
 * Commands:
 * - /health : API health status
 * - /bots   : list last N bot instances
 */
@Component
@ConditionalOnProperty(
    prefix = "quantor.telegram.ops",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class TelegramOpsBot implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(TelegramOpsBot.class);

  // Long-poll tuning:
  // Telegram holds connection up to `timeout` seconds. Client timeout must be > timeout with margin.
  private static final int TG_LONG_POLL_SECONDS = 25;
  private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(75);
  private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final TelegramBotProperties props;
  private final HealthEndpoint healthEndpoint;
  private final BotInstanceRepository botInstanceRepository;
  private final ObjectMapper objectMapper;
  private final HttpClient http;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread pollThread;

  public TelegramOpsBot(
      TelegramBotProperties props,
      HealthEndpoint healthEndpoint,
      BotInstanceRepository botInstanceRepository,
      ObjectMapper objectMapper) {
    this.props = props;
    this.healthEndpoint = healthEndpoint;
    this.botInstanceRepository = botInstanceRepository;
    this.objectMapper = objectMapper;
    this.http = HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();
  }

  // =========================
  // Spring Lifecycle
  // =========================

  @Override
  public void start() {
    log.info("TelegramOpsBot lifecycle start() called");

    if (running.get()) return;

    if (!props.enabled()) {
      log.info("TelegramOpsBot disabled via props (quantor.telegram.enabled=false)");
      return;
    }

    if (props.botToken() == null || props.botToken().isBlank()) {
      log.warn("TelegramOpsBot enabled, but token is empty. Set QUANTOR_TELEGRAM_BOT_TOKEN.");
      return;
    }

    Set<String> allowed = props.allowedUserIdSet();
    if (allowed.isEmpty()) {
      log.warn("TelegramOpsBot enabled but allowedUserIds is empty (all requests denied)");
    }

    running.set(true);
    pollThread = new Thread(this::pollLoop, "quantor-telegram-ops-poll");
    pollThread.setDaemon(true);
    pollThread.start();

    log.info("TelegramOpsBot started");
  }

  @Override
  public void stop() {
    running.set(false);
    if (pollThread != null) pollThread.interrupt();
    log.info("TelegramOpsBot stopped");
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public int getPhase() {
    return 0;
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  // =========================
  // Telegram polling loop
  // =========================

  private void pollLoop() {
    long offset = 0;

    while (running.get() && !Thread.currentThread().isInterrupted()) {
      try {
        GetUpdatesResponse resp = getUpdates(offset);
        if (resp == null || !resp.ok || resp.result == null) {
          continue;
        }

        for (Update upd : resp.result) {
          if (upd == null) continue;

          offset = Math.max(offset, upd.updateId + 1);

          Message msg = upd.message;
          if (msg == null || msg.text == null || msg.chat == null || msg.from == null) {
            continue;
          }

          handleMessage(msg);
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        // keep loop alive
        log.warn("Telegram poll error: {}", e.toString());
        sleepSilently(1500);
      }
    }
  }

  private void handleMessage(Message msg) {
    long fromId = msg.from.id;
    long chatId = msg.chat.id;

    String text = (msg.text == null) ? "" : msg.text.trim();
    if (text.isBlank()) return;

    // Exact allowlist check
    if (!props.allowedUserIdSet().contains(String.valueOf(fromId))) {
      return; // silent deny
    }

    if (text.equals("/start") || text.equals("/help")) {
      sendMessage(chatId, help());
      return;
    }

    if (text.equals("/health")) {
      HealthComponent health = healthEndpoint.health();
      sendMessage(chatId, "API health: " + health.getStatus());
      return;
    }

    if (text.startsWith("/bots")) {
      int limit = parseLimit(text).orElse(10);
      limit = Math.min(Math.max(limit, 1), 50);

      var page =
          botInstanceRepository.findAll(
              PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt")));

      List<BotInstanceEntity> list = page.getContent();
      if (list.isEmpty()) {
        sendMessage(chatId, "No bot instances yet.");
        return;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("Latest bot instances (limit=").append(limit).append("):\n");
      for (BotInstanceEntity bi : list) {
        sb.append("- id=").append(String.valueOf(bi.getId()))
            .append(" userId=").append(bi.getUserId())
            .append(" status=").append(String.valueOf(bi.getStatus()))
            .append(" strategy=").append(String.valueOf(bi.getStrategyId()))
            .append(" symbol=").append(String.valueOf(bi.getSymbol()))
            .append(" interval=").append(String.valueOf(bi.getInterval()))
            .append("\n");
      }

      sendMessage(chatId, sb.toString());
      return;
    }

    sendMessage(chatId, "Unknown command. Send /help");
  }

  private Optional<Integer> parseLimit(String text) {
    String[] parts = text.split("\\s+");
    if (parts.length < 2) return Optional.empty();
    try {
      return Optional.of(Integer.parseInt(parts[1]));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private String help() {
    return "Quantor Ops Bot\n"
        + "Commands:\n"
        + "  /health        - API health status\n"
        + "  /bots [N]      - list latest N bot instances (default 10, max 50)\n";
  }

  private GetUpdatesResponse getUpdates(long offset) throws Exception {
    String url =
        "https://api.telegram.org/bot"
            + props.botToken()
            + "/getUpdates?timeout="
            + TG_LONG_POLL_SECONDS
            + "&offset="
            + offset;

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .timeout(HTTP_REQUEST_TIMEOUT)
            .GET()
            .build();

    try {
      HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());

      int code = resp.statusCode();
      if (code != 200) {
        // 409 = another getUpdates is active with same token (two pollers)
        if (code == 409) {
          log.warn(
              "Telegram getUpdates 409: another poller is active for this bot token. Stop the other instance or disable ops bot.");
          sleepSilently(5000);
          return null;
        }
        log.warn("Telegram getUpdates non-200: {}", code);
        sleepSilently(1200);
        return null;
      }

      return objectMapper.readValue(resp.body(), GetUpdatesResponse.class);
    } catch (java.net.http.HttpTimeoutException e) {
      // Normal for long-polling in imperfect networks; just retry silently
      return null;
    }
  }

  private void sendMessage(long chatId, String text) {
    try {
      String url = "https://api.telegram.org/bot" + props.botToken() + "/sendMessage";
      String body =
          "chat_id="
              + chatId
              + "&text="
              + URLEncoder.encode(text, StandardCharsets.UTF_8)
              + "&disable_web_page_preview=true";

      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(java.net.URI.create(url))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(BodyPublishers.ofString(body))
              .build();

      HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        log.warn("Telegram sendMessage non-200: {}", resp.statusCode());
      }
    } catch (Exception e) {
      log.warn("Telegram sendMessage error: {}", e.toString());
    }
  }

  private void sleepSilently(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // =========================
  // DTOs
  // =========================

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GetUpdatesResponse(boolean ok, List<Update> result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Update(@JsonProperty("update_id") long updateId, Message message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Message(Chat chat, User from, String text) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Chat(long id) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record User(long id, String username) {}
}
