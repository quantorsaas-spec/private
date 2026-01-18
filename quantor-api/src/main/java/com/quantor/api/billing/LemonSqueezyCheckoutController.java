package com.quantor.api.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoints exposing LemonSqueezy checkout (buy) links.
 *
 * Why this exists:
 * - Landing/client apps need a source of truth for the current buy URLs.
 * - We keep the URLs configurable (env / config) instead of hardcoding them into the frontend.
 */
@RestController
@RequestMapping("/api/v1/billing/lemonsqueezy")
public class LemonSqueezyCheckoutController {

    private final String proCheckoutUrl;
    private final String proPlusCheckoutUrl;

    public LemonSqueezyCheckoutController(
            @Value("${quantor.lemonsqueezy.checkoutUrl.pro:}") String proCheckoutUrl,
            @Value("${quantor.lemonsqueezy.checkoutUrl.proPlus:}") String proPlusCheckoutUrl
    ) {
        this.proCheckoutUrl = proCheckoutUrl == null ? "" : proCheckoutUrl.trim();
        this.proPlusCheckoutUrl = proPlusCheckoutUrl == null ? "" : proPlusCheckoutUrl.trim();
    }

    /**
     * Returns configured checkout URLs for the landing/client.
     *
     * Example:
     *   GET /api/v1/billing/lemonsqueezy/checkout
     */
    @GetMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout() {
        return ResponseEntity.ok(Map.of(
                "pro", proCheckoutUrl,
                "proPlus", proPlusCheckoutUrl
        ));
    }
}
