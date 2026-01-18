package com.quantor.exchange;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A very simple client for calling a ChatGPT-like API.
 * URL and API key are taken from config.properties.
 *
 * WARNING: this is only an example of request/response format.
 * For your real endpoint/provider you will need to adjust the JSON
 * (model name, fields, response parsing, etc.).
 */
public class ChatGptClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final String apiUrl;
    private final String apiKey;
    private final OkHttpClient client = new OkHttpClient();

    // --- anti-spam / protection from HTTP 429 ---

    /** How long to wait after 429 (currently 15 minutes). */
    private static final long COOLDOWN_MS = 15 * 60_000L;

    /** Time (millis) until AI is paused. 0 = not paused. */
    private volatile long cooldownUntilMs = 0L;

    public ChatGptClient(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    /** Manually reset the cooldown, if you ever need it. */
    public void resetCooldown() {
        cooldownUntilMs = 0L;
    }

    /**
     * Sends a prompt and returns the raw response body as a string.
     */
    public String sendPrompt(String prompt) throws Exception {

        // ⏸ Protection from repeated calls during 429 cooldown
        if (cooldownUntilMs > 0 && System.currentTimeMillis() < cooldownUntilMs) {
            return "⏸ AI is temporarily paused due to rate limits (HTTP 429). Retry after " +
                    new java.util.Date(cooldownUntilMs);
        }

        String json = """
                {
                  "model": "gpt-4.1-mini",
                  "messages": [
                    {"role":"system","content":"You are a trading coach."},
                    {"role":"user","content":%s}
                  ],
                  "temperature":0.1
                }
                """.formatted(toJsonString(prompt));

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response resp = client.newCall(request).execute()) {

            if (!resp.isSuccessful()) {

                // 🔥 If rate-limited — pause AI requests
                if (resp.code() == 429) {
                    cooldownUntilMs = System.currentTimeMillis() + COOLDOWN_MS;
                    throw new RuntimeException("AI returned 429 — pausing for 15 minutes");
                }

                throw new RuntimeException("ChatGPT error: HTTP " + resp.code());
            }

            return resp.body() != null ? resp.body().string() : "";
        }
    }

    private String toJsonString(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }
}