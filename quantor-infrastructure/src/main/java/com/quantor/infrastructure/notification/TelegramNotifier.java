package com.quantor.infrastructure.notification;

import com.quantor.application.ports.NotifierPort;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TelegramNotifier implements NotifierPort {

    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

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

    @Override


    public void send(String text) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        String json = """
                {
                  "chat_id": "%s",
                  "text": %s,
                  "parse_mode": "HTML"
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

    // ✅ ADDED: main menu (as you had before)
    public void sendMainMenu(String currentModeLabel) {

        String text = """
                📋 BinanceBot Main Menu

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
                """.formatted(currentModeLabel);

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

        String json = """
                {
                  "chat_id": "%s",
                  "text": %s,
                  "parse_mode": "HTML",
                  "reply_markup": %s
                }
                """.formatted(chatId, toJsonString(text), keyboardJson);

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

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

    // ✅ If TelegramCommandBot uses this — keep it
    public void removeKeyboard(String text) {

        String keyboardJson = """
        { "remove_keyboard": true }
        """;

        String json = """
            {
              "chat_id": "%s",
              "text": %s,
              "parse_mode": "HTML",
              "reply_markup": %s
            }
            """.formatted(chatId, toJsonString(text), keyboardJson);

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

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

    private String toJsonString(String s) {
        if (s == null) return "null";
        String escaped = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}