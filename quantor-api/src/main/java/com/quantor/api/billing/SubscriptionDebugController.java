package com.quantor.api.billing;

import com.quantor.api.security.SecurityActor;
import com.quantor.application.ports.SubscriptionPort;
import com.quantor.domain.trading.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class SubscriptionDebugController {

    private final SubscriptionPort subscriptionPort;

    public SubscriptionDebugController(SubscriptionPort subscriptionPort) {
        this.subscriptionPort = subscriptionPort;
    }

    /**
     * Returns subscription status for the authenticated user.
     */
    @GetMapping("/subscription")
    public Map<String, Object> status() {
        var actor = SecurityActor.current();
        if (actor == null || actor.effectiveUserId() == null) {
            throw new IllegalStateException("No authenticated user");
        }
        String userId = actor.effectiveUserId().toString();
        var status = subscriptionPort.status(new UserId(userId)).name();
        return Map.of(
                "userId", userId,
                "status", status
        );
    }
}
