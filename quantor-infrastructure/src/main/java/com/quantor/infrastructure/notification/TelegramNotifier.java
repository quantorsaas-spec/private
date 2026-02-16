package com.quantor.infrastructure.notification;

import com.quantor.application.ports.NotifierPort;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TelegramNotifier implements NotifierPort {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final String botToken;
    private final String chatId;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public TelegramNotifier(String botToken, String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
    }

    /**
     * Core notifier must be PLAIN text.
     * No parse_mode to avoid Telegram HTML parsing errors (e.g. "<UUID>").
     */
    @Override
    public void send(String text) {
        sendPlain(text);
    }

    private void sendPlain(String text) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        String json = """
                {
                  "chat_id": "%s",
                  "text": %s
                }
                """.formatted(chatId, toJsonString(text));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response resp = client.newCall(request).execute()) {
            System.out.println("Telegram response: " + resp.code() + " " +
                    (resp.body() != null ? resp.body().string() : ""));
        } catch (IOException e) {
            System.err.println("Telegram sendMessage error: " + e.getMessage());
        }
    }

    /**
     * Optional HTML sender for controlled templates only.
     */
    private void sendHtml(String html) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        String json = """
                {
                  "chat_id": "%s",
                  "text": %s,
                  "parse_mode": "HTML"
                }
                """.formatted(chatId, toJsonString(html));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response resp = client.newCall(request).execute()) {
            System.out.println("Telegram response: " + resp.code() + " " +
                    (resp.body() != null ? resp.body().string() : ""));
        } catch (IOException e) {
            System.err.println("Telegram sendMessage error: " + e.getMessage());
        }
    }

    // main menu with known-safe HTML (your existing feature)
    public void sendMainMenu(String currentModeLabel) {
        String html = """
                📋 Quantor Main Menu

                Current strategy mode:
                <b>%s</b>

                ▶ /live — LIVE trading (with risk guards)
                📚 /train — TRAIN (no drawdown stop)
                🧪 /paper — paper trading

                📌 /model — show strategy/model parameters
                🧽 /reset_model — reset ONLINE model (weights + file)

                ⏸ /pause — pause
                ▶ /resume — resume
                ⛔ /stop — stop

                🤖 /ai_review — trades overview
                📈 /ai_stats — statistics
                🎯 /ai_trade N — review trade N

                You can press /menu anytime.
                """.formatted(escapeHtml(currentModeLabel));

        String keyboardJson = """
        {
          "keyboard": [
            [
              { "text": "/menu" },
              { "text": "/model" },
              { "text": "/reset_model" }
            ],
            [
              { "text": "/live" },
              { "text": "/train" },
              { "text": "/paper" }
            ],
            [
              { "text": "/pause" },
              { "text": "/resume" },
              { "text": "/stop" }
            ],
            [
              { "text": "/ai_review" },
              { "text": "/ai_stats" }
            ]
          ],
          "resize_keyboard": true,
          "one_time_keyboard": false
        }
        """;

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        String json = """
                {
                  "chat_id": "%s",
                  "text": %s,
                  "parse_mode": "HTML",
                  "reply_markup": %s
                }
                """.formatted(chatId, toJsonString(html), keyboardJson);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response resp = client.newCall(request).execute()) {
            System.out.println("Telegram response: " + resp.code() + " " +
                    (resp.body() != null ? resp.body().string() : ""));
        } catch (IOException e) {
            System.err.println("Telegram sendMainMenu error: " + e.getMessage());
        }
    }

    public void removeKeyboard(String text) {
        String keyboardJson = """
        { "remove_keyboard": true }
        """;

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        String json = """
            {
              "chat_id": "%s",
              "text": %s,
              "reply_markup": %s
            }
            """.formatted(chatId, toJsonString(text), keyboardJson);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response resp = client.newCall(request).execute()) {
            System.out.println("Telegram response: " + resp.code() + " " +
                    (resp.body() != null ? resp.body().string() : ""));
        } catch (IOException e) {
            System.err.println("Telegram removeKeyboard error: " + e.getMessage());
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String toJsonString(String s) {
        if (s == null) return "null";
        String escaped = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
