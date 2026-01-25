package com.quantor.api.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantor.api.rest.dto.ErrorResponse;
import com.quantor.db.SubscriptionSqlPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Lemon Squeezy webhook receiver.
 *
 * Requires:
 *  - env LEMON_SQUEEZY_WEBHOOK_SECRET
 *  - checkout custom user_id so we can map to our local user:
 *    ?checkout[custom][user_id]=<YOUR_USER_ID> 
 *  - signature header X-Signature 
 */
@RestController
@RequestMapping("/api/v1/billing/lemon")
public class LemonWebhookController {

    private final ObjectMapper om;
    private final SubscriptionSqlPort subscriptionPort;

    public LemonWebhookController(ObjectMapper om) {
        this.om = om;
        // infrastructure adapter; simple P0 wiring
        this.subscriptionPort = new SubscriptionSqlPort();
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(HttpServletRequest request) throws IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();
        String signature = request.getHeader("X-Signature");
        String secret = System.getenv("LEMON_SQUEEZY_WEBHOOK_SECRET");

        if (secret == null || secret.isBlank()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("CONFIG_ERROR", "LEMON_SQUEEZY_WEBHOOK_SECRET is not set"));
        }

        if (!LemonSignatureVerifier.verify(rawBody, signature, secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("INVALID_SIGNATURE", "Invalid webhook signature"));
        }

        JsonNode root = om.readTree(rawBody);

        String eventName = text(root.at("/meta/event_name"));
        String userId = text(root.at("/meta/custom_data/user_id"));
        if (userId == null || userId.isBlank()) {
            // Without user_id we can't attach to our local account (hard fail).
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("MISSING_USER_ID", "meta.custom_data.user_id is required"));
        }

        JsonNode data = root.path("data");
        String subscriptionId = text(data.path("id"));
        JsonNode attrs = data.path("attributes");

        Integer variantId = attrs.hasNonNull("variant_id") ? attrs.get("variant_id").asInt() : null;
        String status = text(attrs.path("status"));
        String renewsAt = text(attrs.path("renews_at"));
        String endsAt = text(attrs.path("ends_at"));

        // Track lifecycle events; subscription_updated is the main one.
        // We still upsert on any subscription_* event to stay consistent. 
        if (eventName != null && eventName.startsWith("subscription_")) {
            subscriptionPort.upsert(userId, subscriptionId, variantId, status, renewsAt, endsAt);
        }

        return ResponseEntity.ok().build();
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String v = node.asText();
        return (v == null || v.isBlank()) ? null : v;
    }
}
