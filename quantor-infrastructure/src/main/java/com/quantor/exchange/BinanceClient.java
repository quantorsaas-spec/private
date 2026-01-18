package com.quantor.exchange;


import com.quantor.domain.market.Candle;
import com.quantor.application.ports.ConfigPort;
import com.quantor.infrastructure.config.FileConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Binance client:
 *  - klines()
 *  - marketBuyTestnet()
 *  - marketSellTestnet()
 *
 * Important:
 * - klines() works without API keys
 * - signed requests require BINANCE_API_KEY / BINANCE_API_SECRET
 */
public class BinanceClient {

    private String baseUrl;
    private String apiKey;
    private String apiSecret;
    private boolean testMode;

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper om = new ObjectMapper();

    public BinanceClient() {
        this(defaultConfig());
    }

    private static ConfigPort defaultConfig() {
        try {
            return FileConfigService.defaultFromWorkingDir();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load config from working directory", e);
        }
    }

    public BinanceClient(ConfigPort cfg) {
        Properties p = new Properties();

        // base config
        p.setProperty("mode", cfg.get("mode", "TEST"));
        p.setProperty("baseUrlTest", cfg.get("baseUrlTest", "https://testnet.binance.vision"));
        p.setProperty("baseUrlLive", cfg.get("baseUrlLive", "https://api.binance.com"));

        // secrets (support both new and old key names)
        String apiKey = cfg.getSecret("BINANCE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) apiKey = cfg.getSecret("apiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            p.setProperty("BINANCE_API_KEY", apiKey);
        }

        String apiSecret = cfg.getSecret("BINANCE_API_SECRET");
        if (apiSecret == null || apiSecret.isBlank()) apiSecret = cfg.getSecret("apiSecret");
        if (apiSecret != null && !apiSecret.isBlank()) {
            p.setProperty("BINANCE_API_SECRET", apiSecret);
        }

        initFromProperties(p);
    }

    public BinanceClient(Properties cfg) {
        initFromProperties(cfg);
    }

    private void initFromProperties(Properties cfg) {
        String mode = cfg.getProperty("mode", "TEST").trim().toUpperCase(Locale.ROOT);
        this.testMode = "TEST".equals(mode);

        String baseTest = cfg.getProperty("baseUrlTest", "https://testnet.binance.vision").trim();
        String baseLive = cfg.getProperty("baseUrlLive", "https://api.binance.com").trim();

        this.baseUrl = testMode ? baseTest : baseLive;

        // ✅ read both new keys (secrets.properties) and old ones (fallback)
        this.apiKey = firstNonBlank(
                cfg.getProperty("BINANCE_API_KEY"),
                cfg.getProperty("apiKey", "")
        ).trim();

        this.apiSecret = firstNonBlank(
                cfg.getProperty("BINANCE_API_SECRET"),
                cfg.getProperty("apiSecret", "")
        ).trim();

        System.out.println("✅ Binance mode: " + (testMode ? "TEST" : "LIVE"));
        System.out.println("✅ baseUrl = " + this.baseUrl);
    }

    public static Properties loadConfig(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            Properties p = new Properties();
            p.load(fis);
            return p;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + path, e);
        }
    }

    public boolean isTestnet() {
        return testMode;
    }

    // --------------------------------------------------------------------
    //                              KLINES
    // --------------------------------------------------------------------

    public List<Candle> klines(String symbol, String interval, int limit) throws IOException {

        String url = baseUrl +
                "/api/v3/klines?symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8) +
                "&interval=" + URLEncoder.encode(interval, StandardCharsets.UTF_8) +
                "&limit=" + limit;

        Request req = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response resp = http.newCall(req).execute()) {

            if (!resp.isSuccessful()) {
                throw new IOException("klines HTTP " + resp.code() + ": " +
                        (resp.body() != null ? resp.body().string() : ""));
            }

            String body = resp.body() != null ? resp.body().string() : "[]";
            JsonNode arr = om.readTree(body);

            List<Candle> list = new ArrayList<>();
            for (JsonNode node : arr) {

                long openTimeMs = node.get(0).asLong();
                long closeTimeMs = node.get(6).asLong();

                double open = node.get(1).asDouble();
                double high = node.get(2).asDouble();
                double low = node.get(3).asDouble();
                double close = node.get(4).asDouble();

                double volume = node.get(5).asDouble();
                list.add(new Candle(openTimeMs, open, high, low, close, volume, closeTimeMs));
            }

            return list;
        }
    }

    // --------------------------------------------------------------------
    //                       MARKET ORDERS (TESTNET)
    // --------------------------------------------------------------------

    private void requireKeys() {
        if (apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException(
                    "🚫 Missing BINANCE_API_KEY / BINANCE_API_SECRET (secrets.properties)."
            );
        }
    }

    private String sign(String data) {
        requireKeys();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key =
                    new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }

    private JsonNode signedPost(String path, String query) throws IOException {
        requireKeys();

        long ts = System.currentTimeMillis();
        String q = query + "&timestamp=" + ts;
        String sig = sign(q);

        String url = baseUrl + path + "?" + q + "&signature=" + sig;

        // ✅ IMPORTANT: OkHttp 4.x — empty body must be created this way
        RequestBody emptyBody = RequestBody.create(new byte[0], null);

        Request req = new Request.Builder()
                .url(url)
                .post(emptyBody)
                .addHeader("X-MBX-APIKEY", apiKey)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("ORDER " + resp.code() + ": " + body);
            }
            if (body.isEmpty()) return om.createObjectNode();
            return om.readTree(body);
        }
    }

    private static final DecimalFormat QTY_FMT;

    static {
        DecimalFormatSymbols dfs = new DecimalFormatSymbols(Locale.US);
        QTY_FMT = new DecimalFormat("0.########", dfs);
        QTY_FMT.setGroupingUsed(false);
    }

    private JsonNode exchangeInfo() throws IOException {
        String url = baseUrl + "/api/v3/exchangeInfo";
        Request req = new Request.Builder().url(url).get().build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("exchangeInfo error: " + resp.code());
            }
            String body = resp.body() != null ? resp.body().string() : "";
            return om.readTree(body);
        }
    }

    private double adjustQtyToLotSize(String symbol, double qty) throws IOException {
        JsonNode info = exchangeInfo();

        for (JsonNode s : info.get("symbols")) {
            if (!s.get("symbol").asText().equals(symbol)) continue;

            for (JsonNode f : s.get("filters")) {
                if (!f.get("filterType").asText().equals("LOT_SIZE")) continue;

                double minQty = f.get("minQty").asDouble();
                double step = f.get("stepSize").asDouble();

                if (qty < minQty) {
                    throw new IllegalArgumentException("Qty too small. minQty=" + minQty);
                }

                double rounded = Math.floor(qty / step) * step;

                return new java.math.BigDecimal(rounded)
                        .stripTrailingZeros()
                        .doubleValue();
            }
        }
        throw new IllegalStateException("LOT_SIZE filter not found for " + symbol);
    }

    public void marketBuyTestnet(String symbol, double qty) throws IOException {
        if (!testMode) throw new IllegalStateException("Not in TEST mode");

        double fixedQty = adjustQtyToLotSize(symbol, qty);
        String q = QTY_FMT.format(fixedQty);

        String query = "symbol=" + symbol +
                "&side=BUY" +
                "&type=MARKET" +
                "&quantity=" + q;

        JsonNode resp = signedPost("/api/v3/order", query);
        System.out.println("[TEST BUY] " + symbol + " qty=" + q + " resp=" + resp);
    }

    public void marketSellTestnet(String symbol, double qty) throws IOException {
        if (!testMode) throw new IllegalStateException("Not in TEST mode");

        double fixedQty = adjustQtyToLotSize(symbol, qty);
        String q = QTY_FMT.format(fixedQty);

        String query = "symbol=" + symbol +
                "&side=SELL" +
                "&type=MARKET" +
                "&quantity=" + q;

        JsonNode resp = signedPost("/api/v3/order", query);
        System.out.println("[TEST SELL] " + symbol + " qty=" + q + " resp=" + resp);
    }
}