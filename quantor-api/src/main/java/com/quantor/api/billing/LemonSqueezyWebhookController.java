package com.quantor.api.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.api.billing.lemonsqueezy.LemonSqueezySignature;
import com.quantor.api.events.IdempotentEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Lemon Squeezy webhook endpoint.
 *
 * Verifies X-Signature (HMAC SHA-256) if quantor.lemonsqueezy.webhookSecret is configured.
 * Event name is in X-Event-Name header.
 *
 * Docs:
 * - X-Signature header and signing secret: https://docs.lemonsqueezy.com/help/webhooks/signing-requests
 * - X-Event-Name header: https://docs.lemonsqueezy.com/help/webhooks/webhook-requests
 */
@RestController
@RequestMapping("/api/v1/billing/lemonsqueezy")
public class LemonSqueezyWebhookController {

    private static final String SOURCE = "LEMONSQUEEZY";

    private final LemonSqueezySignature signature;
    private final LemonSqueezyWebhookService sync;
    private final IdempotentEventService idempotency;
    private final ObjectMapper mapper;

    public LemonSqueezyWebhookController(
            LemonSqueezySignature signature,
            LemonSqueezyWebhookService sync,
            IdempotentEventService idempotency,
            ObjectMapper mapper
    ) {
        this.signature = signature;
        this.sync = sync;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(value = "X-Event-Name", required = false) String eventName,
            @RequestHeader(value = "X-Signature", required = false) String xSignature,
            @RequestBody String rawBody
    ) {
        boolean verified = signature.verifyOrBypass(rawBody, xSignature);
        if (!verified) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "error",
                    "reason", "invalid_signature",
                    "ts", Instant.now().toString()
            ));
        }

        String safeEventName = (eventName == null || eventName.isBlank()) ? "unknown" : eventName.trim();

        // Idempotency: prefer explicit event id from payload; fallback to sha256(body)
        String eventId = extractEventIdBestEffort(rawBody);
        UUID userId = extractUserIdBestEffort(rawBody);

        var row = idempotency.tryStart(SOURCE, eventId, safeEventName, userId, rawBody);
        if (row.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "verified", true,
                    "event", safeEventName,
                    "idempotent", true,
                    "reason", "duplicate",
                    "receivedAt", Instant.now().toString()
            ));
        }

        try {
            LemonSqueezyWebhookService.SyncResult r = sync.handle(safeEventName, rawBody);
            idempotency.markProcessed(row.get().getId());
            return ResponseEntity.ok(Map.of(
                    "status", r.status(),
                    "reason", r.reason(),
                    "event", safeEventName,
                    "userId", r.userId(),
                    "plan", r.plan(),
                    "subscriptionStatus", r.subscriptionStatus(),
                    "externalId", r.externalId(),
                    "receivedAt", Instant.now().toString()
            ));
        } catch (Exception e) {
            idempotency.markFailed(row.get().getId(), e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "reason", "sync_failed",
                    "event", safeEventName,
                    "receivedAt", Instant.now().toString()
            ));
        }
    }

    private String extractEventIdBestEffort(String rawBody) {
        try {
            JsonNode root = mapper.readTree(rawBody);
            String v = readText(root, "meta", "event_id");
            if (v == null) v = readText(root, "meta", "eventId");
            if (v == null) v = readText(root, "meta", "id");
            if (v != null && !v.isBlank()) return v.trim();
        } catch (Exception ignored) {}
        return sha256Hex(rawBody);
    }

    private UUID extractUserIdBestEffort(String rawBody) {
        try {
            JsonNode root = mapper.readTree(rawBody);
            String v = readText(root, "meta", "custom_data", "user_id");
            if (v == null) v = readText(root, "meta", "custom_data", "userId");
            if (v != null && !v.isBlank()) return UUID.fromString(v.trim());
        } catch (Exception ignored) {}
        return null;
    }

    private static String readText(JsonNode root, String... path) {
        JsonNode n = root;
        for (String p : path) {
            if (n == null) return null;
            n = n.get(p);
        }
        return (n != null && n.isValueNode()) ? n.asText() : null;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            // worst-case fallback
            return Integer.toHexString(s.hashCode());
        }
    }
}
