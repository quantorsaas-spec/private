package com.quantor.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.application.ports.ConfigPort;
import com.quantor.domain.market.Candle;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Minimal Bybit REST client (v5).
 *
 * <p>Focuses on what Quantor needs right now:
 * <ul>
 *   <li>Market data: kline/candles (public)</li>
 *   <li>Spot market orders (private, HMAC auth)</li>
 * </ul>
 *
 * <p>Config keys (env or properties):
 * <ul>
 *   <li>BYBIT_BASE_URL (default https://api.bybit.com, testnet: https://api-testnet.bybit.com)</li>
 *   <li>BYBIT_API_KEY</li>
 *   <li>BYBIT_API_SECRET</li>
 *   <li>BYBIT_RECV_WINDOW (default 5000)</li>
 * </ul>
 */
public final class BybitClient {

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final String recvWindow;

    private final OkHttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public BybitClient(ConfigPort config) {
        Objects.requireNonNull(config, "config");
        this.baseUrl = firstNonBlank(config.get("BYBIT_BASE_URL", ""), "https://api.bybit.com");
        this.apiKey = config.get("BYBIT_API_KEY", "");
        this.apiSecret = config.get("BYBIT_API_SECRET", "");
        this.recvWindow = firstNonBlank(config.get("BYBIT_RECV_WINDOW", ""), "5000");

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
     * Bybit v5 Get Kline.
     *
     * Docs: GET /v5/market/kline (category=spot)
     */
    public List<Candle> klinesSpot(String symbol, String interval, int limit) throws Exception {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "/v5/market/kline"))
                .newBuilder()
                .addQueryParameter("category", "spot")
                .addQueryParameter("symbol", symbol)
                .addQueryParameter("interval", interval)
                .addQueryParameter("limit", String.valueOf(limit))
                .build();

        Request req = new Request.Builder()
                .get()
                .url(url)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("Bybit klines failed: HTTP " + resp.code());
            }
            String body = Objects.requireNonNull(resp.body()).string();
            JsonNode root = om.readTree(body);
            int retCode = root.path("retCode").asInt(-1);
            if (retCode != 0) {
                throw new IllegalStateException("Bybit klines error: " + root.path("retMsg").asText());
            }
            JsonNode list = root.path("result").path("list");
            List<Candle> out = new ArrayList<>();
            if (list.isArray()) {
                for (JsonNode row : list) {
                    // row: [startTime, open, high, low, close, volume, turnover]
                    long openTime = row.get(0).asLong();
                    double open = row.get(1).asDouble();
                    double high = row.get(2).asDouble();
                    double low = row.get(3).asDouble();
                    double close = row.get(4).asDouble();
                    double volume = row.get(5).asDouble();
                    // Bybit returns reverse-sorted by startTime; keep order as-is (engine usually uses latest)
                    out.add(new Candle(openTime, open, high, low, close, volume, openTime));
                }
            }
            return out;
        }
    }

    /**
     * Place a spot MARKET order.
     *
     * Docs: POST /v5/order/create (category=spot)
     */
    public void placeSpotMarketOrder(String symbol, String side, double qty) throws Exception {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException("BYBIT_API_KEY/BYBIT_API_SECRET are not configured");
        }
        String json = om.createObjectNode()
                .put("category", "spot")
                .put("symbol", symbol)
                .put("side", side) // Buy / Sell
                .put("orderType", "Market")
                .put("qty", String.valueOf(qty))
                .toString();

        String timestamp = String.valueOf(System.currentTimeMillis());
        String payloadToSign = timestamp + apiKey + recvWindow + json;
        String sign = hmacSha256Hex(apiSecret, payloadToSign);

        Request req = new Request.Builder()
                .url(baseUrl + "/v5/order/create")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-BAPI-API-KEY", apiKey)
                .addHeader("X-BAPI-TIMESTAMP", timestamp)
                .addHeader("X-BAPI-RECV-WINDOW", recvWindow)
                .addHeader("X-BAPI-SIGN", sign)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("Bybit order failed: HTTP " + resp.code() + " " + body);
            }
            JsonNode root = om.readTree(body);
            int retCode = root.path("retCode").asInt(-1);
            if (retCode != 0) {
                throw new IllegalStateException("Bybit order error: " + root.path("retMsg").asText() + " body=" + body);
            }
        }
    }

    private static String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
