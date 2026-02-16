// File: quantor-api/src/main/java/com/quantor/api/webhooks/LemonSqueezyWebhookController.java
package com.quantor.api.webhooks;

import com.quantor.api.billing.LemonSqueezyWebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * ⚠ LEGACY Lemon Squeezy webhook controller.
 *
 * OFF by default to avoid bean conflicts.
 * Use ONLY if you explicitly enable:
 *
 *   quantor.webhooks.legacyLemon.enabled=true
 *
 * Canonical controller:
 *   com.quantor.api.billing.LemonSqueezyWebhookController
 */
@RestController
@ConditionalOnProperty(
        prefix = "quantor.webhooks.legacyLemon",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequestMapping("/api/v1/webhooks/lemonsqueezy")
public class LemonSqueezyWebhookController {

    private final LemonSqueezyWebhookService service;
    private final String webhookSecret;

    public LemonSqueezyWebhookController(
            LemonSqueezyWebhookService service,
            @Value("${quantor.lemonsqueezy.webhookSecret:}") String webhookSecret
    ) {
        this.service = service;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handle(
            @RequestHeader(name = "X-Signature", required = false) String signature,
            @RequestHeader(name = "X-Event-Name", required = false) String eventName,
            @RequestBody byte[] rawBody
    ) {
        // Fail-closed
        if (!isValidSignature(signature, rawBody)) {
            return ResponseEntity.status(401).body("invalid_signature");
        }

        String body = new String(rawBody, StandardCharsets.UTF_8);
        LemonSqueezyWebhookService.SyncResult r = service.handle(eventName, body);

        // Lemon retries on non-2xx
        if (r.reason() != null && !r.reason().isBlank()) {
            return ResponseEntity.ok(r.status() + ":" + r.reason());
        }
        return ResponseEntity.ok(r.status());
    }

    private boolean isValidSignature(String signature, byte[] rawBody) {
        if (!StringUtils.hasText(webhookSecret)) return false;
        if (!StringUtils.hasText(signature)) return false;

        String expectedHex = hmacSha256Hex(webhookSecret, rawBody);
        if (!StringUtils.hasText(expectedHex)) return false;

        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hmacSha256Hex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }
}
