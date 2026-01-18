package com.quantor.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.application.ports.ConfigPort;
import com.quantor.domain.market.Candle;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Minimal OKX REST client (v5) for Quantor.
 *
 * <p>Focuses on what Quantor needs right now:
 * <ul>
 *   <li>Market data: candles (public)</li>
 *   <li>Spot market orders (private, HMAC auth)</li>
 * </ul>
 *
 * <p>Config keys (env or properties):
 * <ul>
 *   <li>OKX_BASE_URL (default https://www.okx.com; demo: https://www.okx.com)</li>
 *   <li>OKX_API_KEY</li>
 *   <li>OKX_API_SECRET</li>
 *   <li>OKX_PASSPHRASE</li>
 * </ul>
 */
public final class OkxClient {

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;

    private final OkHttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public OkxClient(ConfigPort config) {
        Objects.requireNonNull(config, "config");
        this.baseUrl = firstNonBlank(config.get("OKX_BASE_URL", ""), "https://www.okx.com");
        this.apiKey = config.get("OKX_API_KEY", "");
        this.apiSecret = config.get("OKX_API_SECRET", "");
        this.passphrase = config.get("OKX_PASSPHRASE", "");

        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static String firstNonBlank(String a, String fallback) {
        if (a != null && !a.isBlank()) return a;
        return fallback;
    }

    /**
     * OKX v5 market candles.
     *
     * Endpoint: GET /api/v5/market/candles
     * Params: instId=BTC-USDT, bar=1m, limit=100
     */
    public List<Candle> candles(String instId, String bar, int limit) throws Exception {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "/api/v5/market/candles"))
                .newBuilder()
                .addQueryParameter("instId", instId)
                .addQueryParameter("bar", bar)
                .addQueryParameter("limit", String.valueOf(limit))
                .build();

        Request req = new Request.Builder().get().url(url).build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("OKX candles failed: HTTP " + resp.code());
            }
            String body = Objects.requireNonNull(resp.body()).string();
            JsonNode root = om.readTree(body);
            String code = root.path("code").asText();
            if (!"0".equals(code)) {
                throw new IllegalStateException("OKX candles error: " + root.path("msg").asText() + " body=" + body);
            }
            JsonNode data = root.path("data");
            List<Candle> out = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode row : data) {
                    // row: [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]
                    long openTime = row.get(0).asLong();
                    double open = row.get(1).asDouble();
                    double high = row.get(2).asDouble();
                    double low = row.get(3).asDouble();
                    double close = row.get(4).asDouble();
                    double volume = row.get(5).asDouble();
                    out.add(new Candle(openTime, open, high, low, close, volume, openTime));
                }
            }
            return out;
        }
    }

    /**
     * Place a spot MARKET order.
     *
     * Endpoint: POST /api/v5/trade/order
     * Body: {"instId":"BTC-USDT","tdMode":"cash","side":"buy","ordType":"market","sz":"0.001"}
     */
    public void placeSpotMarketOrder(String instId, String side, double sizeBaseQty) throws Exception {
        ensureAuthConfigured();

        String json = om.createObjectNode()
                .put("instId", instId)
                .put("tdMode", "cash")
                .put("side", side) // buy / sell
                .put("ordType", "market")
                .put("sz", String.valueOf(sizeBaseQty))
                .toString();

        String method = "POST";
        String path = "/api/v5/trade/order";
        String timestamp = Instant.now().toString();
        String sign = sign(timestamp, method, path, json);

        Request req = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("OK-ACCESS-KEY", apiKey)
                .addHeader("OK-ACCESS-SIGN", sign)
                .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                .addHeader("OK-ACCESS-PASSPHRASE", passphrase)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("OKX order failed: HTTP " + resp.code() + " " + body);
            }
            JsonNode root = om.readTree(body);
            String code = root.path("code").asText();
            if (!"0".equals(code)) {
                throw new IllegalStateException("OKX order error: " + root.path("msg").asText() + " body=" + body);
            }
        }
    }

    private void ensureAuthConfigured() {
        if (apiKey.isBlank() || apiSecret.isBlank() || passphrase.isBlank()) {
            throw new IllegalStateException("OKX_API_KEY/OKX_API_SECRET/OKX_PASSPHRASE are not configured");
        }
    }

    private String sign(String timestamp, String method, String requestPath, String body) throws Exception {
        String prehash = timestamp + method.toUpperCase() + requestPath + (body == null ? "" : body);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(raw);
    }
}
