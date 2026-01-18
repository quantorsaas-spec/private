package com.quantor.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.application.ports.ConfigPort;
import com.quantor.domain.market.Candle;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Minimal Coinbase Exchange REST client (spot) for Quantor MVP.
 *
 * <p>This implementation targets the Coinbase Exchange REST API (not the Coinbase retail v3 brokerage API).
 *
 * <p>Public:
 * <ul>
 *   <li>GET /products/{product_id}/candles</li>
 * </ul>
 *
 * <p>Private:
 * <ul>
 *   <li>POST /orders (market)</li>
 * </ul>
 *
 * <p>Auth: CB-ACCESS-KEY / CB-ACCESS-SIGN / CB-ACCESS-TIMESTAMP / CB-ACCESS-PASSPHRASE.
 * Signature: base64(HMAC_SHA256(base64_decode(secret), prehash)).
 * Prehash: timestamp + method + requestPath + body.
 */
public final class CoinbaseClient {

    private static final String DEFAULT_BASE_URL = "https://api.exchange.coinbase.com";
    private static final String DEFAULT_SANDBOX_BASE_URL = "https://api-public.sandbox.exchange.coinbase.com";

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecretBase64;
    private final String passphrase;

    private final OkHttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public CoinbaseClient(ConfigPort config) {
        Objects.requireNonNull(config, "config");

        boolean sandbox = Boolean.parseBoolean(config.get("COINBASE_SANDBOX", "false"));
        String base = config.get("COINBASE_BASE_URL", "");
        if (base == null || base.isBlank()) {
            base = sandbox ? DEFAULT_SANDBOX_BASE_URL : DEFAULT_BASE_URL;
        }

        this.baseUrl = base;
        this.apiKey = config.get("COINBASE_API_KEY", "");
        this.apiSecretBase64 = config.get("COINBASE_API_SECRET", "");
        this.passphrase = config.get("COINBASE_PASSPHRASE", "");

        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private void requireKeys() {
        if (apiKey == null || apiKey.isBlank()
                || apiSecretBase64 == null || apiSecretBase64.isBlank()
                || passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException(
                    "Missing Coinbase credentials. Configure COINBASE_API_KEY / COINBASE_API_SECRET / COINBASE_PASSPHRASE"
            );
        }
    }

    /**
     * Coinbase candles.
     *
     * @param productId    Coinbase product id, e.g. BTC-USD
     * @param granularityS bucket size in seconds (allowed: 60, 300, 900, 3600, 21600, 86400)
     * @param limit        desired number of candles (Coinbase typically returns up to ~300 per request)
     */
    public List<Candle> candles(String productId, int granularityS, int limit) throws Exception {
        Objects.requireNonNull(productId, "productId");
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");

        // Coinbase Exchange API returns most recent first. We request a window that should cover the limit.
        Instant end = Instant.now();
        Instant start = end.minusSeconds((long) granularityS * Math.min(limit, 300));

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "/products/" + productId + "/candles"))
                .newBuilder()
                .addQueryParameter("granularity", String.valueOf(granularityS))
                .addQueryParameter("start", start.toString())
                .addQueryParameter("end", end.toString())
                .build();

        Request req = new Request.Builder()
                .get()
                .url(url)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("Coinbase candles failed: HTTP " + resp.code() + " => " + body);
            }

            JsonNode arr = om.readTree(body);
            List<Candle> out = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode row : arr) {
                    // [ time, low, high, open, close, volume ] (seconds)
                    long openTimeSec = row.get(0).asLong();
                    double low = row.get(1).asDouble();
                    double high = row.get(2).asDouble();
                    double open = row.get(3).asDouble();
                    double close = row.get(4).asDouble();
                    double volume = row.get(5).asDouble();

                    long openMs = openTimeSec * 1000L;
                    long closeMs = openMs + (granularityS * 1000L) - 1L;
                    out.add(new Candle(openMs, open, high, low, close, volume, closeMs));
                }
            }

            // Ensure ascending order by time (engine/strategies generally expect chronological series)
            out.sort(Comparator.comparingLong(Candle::openTime));
            // Trim to last 'limit' candles if Coinbase returned more.
            if (out.size() > limit) {
                return out.subList(out.size() - limit, out.size());
            }
            return out;
        }
    }

    public void placeMarketOrder(String productId, String side, double sizeBase) throws Exception {
        requireKeys();
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(side, "side");
        if (sizeBase <= 0) throw new IllegalArgumentException("sizeBase must be > 0");

        // Exchange API expects side: buy|sell and type: market
        String json = om.createObjectNode()
                .put("type", "market")
                .put("side", side.toLowerCase())
                .put("product_id", productId)
                // Coinbase expects string values
                .put("size", stripTrailingZeros(sizeBase))
                .toString();

        String path = "/orders";
        String ts = String.valueOf(Instant.now().getEpochSecond());
        Headers headers = signedHeaders(ts, "POST", path, json);

        Request req = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .headers(headers)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("Coinbase order failed: HTTP " + resp.code() + " => " + body);
            }
        }
    }

    private Headers signedHeaders(String timestampSec, String method, String requestPath, String body) {
        requireKeys();
        String prehash = timestampSec + method.toUpperCase() + requestPath + (body == null ? "" : body);
        String signature = signBase64(apiSecretBase64, prehash);
        return new Headers.Builder()
                .add("CB-ACCESS-KEY", apiKey)
                .add("CB-ACCESS-SIGN", signature)
                .add("CB-ACCESS-TIMESTAMP", timestampSec)
                .add("CB-ACCESS-PASSPHRASE", passphrase)
                .add("Content-Type", "application/json")
                .build();
    }

    private static String signBase64(String secretBase64, String prehash) {
        try {
            byte[] secret = Base64.getDecoder().decode(secretBase64);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign Coinbase request", e);
        }
    }

    private static String stripTrailingZeros(double v) {
        // Avoid scientific notation in JSON
        String s = Double.toString(v);
        if (s.contains("E") || s.contains("e")) {
            // fall back to plain format
            return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
        }
        if (s.indexOf('.') >= 0) {
            // remove trailing zeros
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
